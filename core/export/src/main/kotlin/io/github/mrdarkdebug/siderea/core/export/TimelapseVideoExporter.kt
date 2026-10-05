package io.github.mrdarkdebug.siderea.core.export

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

/** The video could not be made; [message] is written for the person using the phone. */
class ExportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class ExportPhase { PREPARING, MEASURING, ENCODING, FINISHING }

data class ExportProgress(
    val phase: ExportPhase,
    val done: Int,
    val total: Int,
)

data class VideoResult(
    val file: File,
    val frames: Int,
    /** Frames that could not be decoded (for example a half-written JPEG after a crash) and were left out. */
    val skipped: Int,
    val size: PixelSize,
    val bitrate: Int,
    val seconds: Double,
    /** True when the phone's encoder could not take the requested size and Siderea made it smaller. */
    val shrunk: Boolean,
)

/**
 * Turns a session's JPEG frames into an MP4 with the phone's hardware encoder (`MediaCodec` + `MediaMuxer`).
 * Crop, scale and deflicker are applied while each frame is drawn onto the encoder's input surface.
 */
class TimelapseVideoExporter {
    /**
     * Encodes [frames] in order into [output]. Cancelling the calling coroutine stops the encoder and deletes the
     * partial file. Throws [ExportException] when the video cannot be made.
     */
    suspend fun export(
        frames: List<File>,
        spec: VideoSpec,
        output: File,
        onProgress: (ExportProgress) -> Unit = {},
    ): VideoResult =
        withContext(Dispatchers.Default) {
            try {
                run(frames, spec, output, onProgress)
            } catch (e: CancellationException) {
                output.delete()
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                output.delete()
                throw if (e is ExportException) e else ExportException(describe(e), e)
            }
        }

    private suspend fun run(
        frames: List<File>,
        spec: VideoSpec,
        output: File,
        onProgress: (ExportProgress) -> Unit,
    ): VideoResult {
        if (frames.isEmpty()) throw ExportException("There are no frames to turn into a video.")
        onProgress(ExportProgress(ExportPhase.PREPARING, 0, frames.size))
        val plan = plan(frames, spec)
        val gains = measure(frames, spec, onProgress)
        output.parentFile?.mkdirs()

        val session =
            VideoEncodeSession(plan.encoder, spec.codec, plan.size, spec.fps, plan.bitrate, output)
        try {
            var skipped = 0
            frames.forEachIndexed { index, file ->
                coroutineContext.ensureActive()
                val drawn = drawFrame(session, file, plan, gains[index])
                if (!drawn) skipped++
                onProgress(ExportProgress(ExportPhase.ENCODING, index + 1, frames.size))
            }
            if (skipped == frames.size) throw ExportException("None of the frames could be read.")
            onProgress(ExportProgress(ExportPhase.FINISHING, frames.size, frames.size))
            session.finish()
            val written = session.framesWritten
            return VideoResult(
                file = output,
                frames = written,
                skipped = skipped,
                size = plan.size,
                bitrate = plan.bitrate,
                seconds = ExportMath.videoSeconds(written, spec.fps),
                shrunk = plan.shrunk,
            )
        } finally {
            session.release()
        }
    }

    private class Plan(
        val encoder: android.media.MediaCodecInfo,
        val source: PixelSize,
        val crop: PixelRect,
        val size: PixelSize,
        val bitrate: Int,
        val shrunk: Boolean,
    )

    private fun plan(
        frames: List<File>,
        spec: VideoSpec,
    ): Plan {
        val encoder =
            VideoEncoders.find(spec.codec)
                ?: throw ExportException("This phone cannot encode ${spec.codec.label} video. Try another format.")
        val source =
            frames.firstNotNullOfOrNull { runCatching { FrameDecoder.size(it) }.getOrNull() }
                ?: throw ExportException("None of the frames could be read.")
        val crop = ExportMath.cropRect(source.width, source.height, spec.crop, spec.panX, spec.panY)
        val wanted = ExportMath.outputSize(crop.width, crop.height, spec.size)
        val fitted =
            ExportMath.fitToEncoder(VideoEncoders.aligned(encoder, spec.codec, wanted)) { candidate ->
                VideoEncoders.supports(
                    encoder,
                    spec.codec,
                    VideoEncoders.aligned(encoder, spec.codec, candidate),
                    spec.fps,
                )
            }
                ?: throw ExportException(
                    "This phone's encoder cannot make video at ${spec.fps} fps. Try a lower frame rate.",
                )
        val size = VideoEncoders.aligned(encoder, spec.codec, fitted)
        return Plan(encoder, source, crop, size, ExportMath.bitrate(size, spec), size != wanted)
    }

    /** One brightness gain per frame; all 1.0 when deflicker is off. */
    private suspend fun measure(
        frames: List<File>,
        spec: VideoSpec,
        onProgress: (ExportProgress) -> Unit,
    ): DoubleArray {
        if (spec.deflicker == DeflickerLevel.OFF) return DoubleArray(frames.size) { 1.0 }
        val luma = DoubleArray(frames.size) { Double.NaN }
        frames.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            runCatching {
                val bitmap = FrameDecoder.decode(file, MEASURE_LONG_SIDE, software = true)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap.recycle()
                luma[index] = Deflicker.meanLuma(pixels)
            }
            onProgress(ExportProgress(ExportPhase.MEASURING, index + 1, frames.size))
        }
        fillGaps(luma)
        return Deflicker.gains(luma, spec.deflicker.window)
    }

    /** A frame that could not be measured borrows the brightness of the nearest one that could. */
    private fun fillGaps(luma: DoubleArray) {
        val known = luma.indices.filter { !luma[it].isNaN() }
        if (known.isEmpty()) {
            luma.fill(1.0)
            return
        }
        for (i in luma.indices) {
            if (luma[i].isNaN()) luma[i] = luma[known.minBy { kotlin.math.abs(it - i) }]
        }
    }

    /** Draws [file] onto the encoder. Returns false when the frame could not be decoded and was skipped. */
    private fun drawFrame(
        session: VideoEncodeSession,
        file: File,
        plan: Plan,
        gain: Double,
    ): Boolean {
        val outLong = maxOf(plan.size.width, plan.size.height).toDouble()
        val scale = maxOf(plan.source.width, plan.source.height) / maxOf(plan.crop.width, plan.crop.height).toDouble()
        val needLong = ceil(outLong * scale).toInt().coerceAtMost(session.maxTextureSize)
        val bitmap = runCatching { FrameDecoder.decode(file, needLong, software = true) }.getOrNull() ?: return false
        try {
            val sx = bitmap.width.toDouble() / plan.source.width
            val sy = bitmap.height.toDouble() / plan.source.height
            val from =
                PixelRect(
                    (plan.crop.left * sx).toInt(),
                    (plan.crop.top * sy).toInt(),
                    (plan.crop.right * sx).toInt().coerceAtMost(bitmap.width),
                    (plan.crop.bottom * sy).toInt().coerceAtMost(bitmap.height),
                )
            session.draw(bitmap, from, gain.toFloat())
        } finally {
            bitmap.recycle()
        }
        return true
    }

    private fun describe(e: Exception): String =
        when (e) {
            is java.io.IOException -> "Siderea couldn't write the video file. Is there enough free space?"
            is IllegalStateException -> e.message ?: "The encoder stopped unexpectedly."
            else -> "The video could not be made: ${e.message ?: e.javaClass.simpleName}"
        }

    private companion object {
        const val MEASURE_LONG_SIDE = 240
    }
}
