package io.github.mrdarkdebug.siderea.core.export

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File

/**
 * One hardware encoder feeding one MP4 muxer. Frames are drawn onto [surface]; a drain thread pulls the encoded
 * output and writes it, so a slow encoder can never deadlock the thread that is drawing.
 *
 * Each frame is stamped `index / fps` through OpenGL, so the video's timeline is exact whatever the wall clock did.
 * Everything except the drain thread must stay on the thread that created the session (EGL is thread-bound).
 */
internal class VideoEncodeSession(
    info: MediaCodecInfo,
    codec: VideoCodec,
    size: PixelSize,
    private val fps: Int,
    bitrate: Int,
    output: File,
) {
    private val encoder: MediaCodec = MediaCodec.createByCodecName(info.name)
    private val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()
    private lateinit var drainer: Thread
    private var track = -1
    private var submitted = 0
    private lateinit var renderer: GlFrameRenderer

    @Volatile private var drainFailure: Throwable? = null

    @Volatile private var abort = false
    private var muxerStarted = false
    private var written = 0
    private var released = false

    private lateinit var surface: Surface

    /** The largest bitmap side the GPU can draw from; decode frames no larger than this. */
    val maxTextureSize: Int get() = renderer.maxTextureSize

    /** Frames the muxer actually received. Meaningful once [finish] has returned. */
    val framesWritten: Int get() = written

    init {
        try {
            configureEncoder(codec, size, bitrate)
            renderer = GlFrameRenderer(surface, size)
            drainer = Thread(::drainLoop, "siderea-encoder-drain").also { it.start() }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            release()
            throw e
        }
    }

    private fun configureEncoder(
        codec: VideoCodec,
        size: PixelSize,
        bitrate: Int,
    ) {
        val format = MediaFormat.createVideoFormat(codec.mime, size.width, size.height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_SECONDS)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = encoder.createInputSurface()
        encoder.start()
    }

    /** Draws the [crop] of [bitmap] (CPU bitmap, crop in its own pixels) times [gain] as the next frame. */
    fun draw(
        bitmap: Bitmap,
        crop: PixelRect,
        gain: Float,
    ) {
        drainFailure?.let { throw it }
        renderer.draw(bitmap, crop, gain, submitted * NANOS / fps)
        submitted++
    }

    /** Ends the stream and waits for every frame to be written. Throws if the encoder failed. */
    fun finish() {
        drainFailure?.let { throw it }
        encoder.signalEndOfInputStream()
        drainer.join(FINISH_TIMEOUT_MS)
        if (drainer.isAlive) {
            abort = true
            throw IllegalStateException("The encoder did not finish in time")
        }
        drainFailure?.let { throw it }
        check(muxerStarted && written > 0) { "The encoder produced no video" }
        muxer.stop()
    }

    /** Frees everything. Safe to call after [finish] and after a failure. */
    fun release() {
        if (released) return
        released = true
        abort = true
        if (::drainer.isInitialized) runCatching { drainer.join(JOIN_MS) }
        if (::renderer.isInitialized) runCatching { renderer.release() }
        if (::surface.isInitialized) runCatching { surface.release() }
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching { muxer.release() }
    }

    private fun drainLoop() {
        try {
            var done = false
            while (!done && !abort) {
                done = drainOnce()
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            drainFailure = e
        }
    }

    /** Handles one encoder output event. Returns true after the end-of-stream buffer. */
    private fun drainOnce(): Boolean {
        val index = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
        when {
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                track = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                muxerStarted = true
            }

            index >= 0 -> {
                val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (!isConfig && bufferInfo.size > 0 && muxerStarted) {
                    val data = checkNotNull(encoder.getOutputBuffer(index))
                    data.position(bufferInfo.offset).limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(track, data, bufferInfo)
                    written++
                }
                encoder.releaseOutputBuffer(index, false)
                return bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }
        return false
    }

    private companion object {
        const val KEY_FRAME_SECONDS = 1
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val FINISH_TIMEOUT_MS = 60_000L
        const val JOIN_MS = 2_000L
        const val NANOS = 1_000_000_000L
    }
}
