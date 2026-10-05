package io.github.mrdarkdebug.siderea.core.export

import android.media.MediaCodecInfo
import android.media.MediaCodecList

/** What the phone's video encoders can really do. Asked at export time, never assumed. */
object VideoEncoders {
    /** The best surface-input encoder for [codec]: a hardware one when the phone has it. */
    fun find(codec: VideoCodec): MediaCodecInfo? =
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .filter {
                it.isEncoder &&
                    codec.mime in it.supportedTypes &&
                    (codec != VideoCodec.AV1 || it.isHardwareAccelerated) &&
                    acceptsSurface(it, codec)
            }.minByOrNull { if (it.isHardwareAccelerated) 0 else 1 }

    /** The codecs this phone can encode to. */
    fun available(): List<VideoCodec> = VideoCodec.entries.filter { find(it) != null }

    /** Rounds [size] down to the multiples this encoder wants. */
    fun aligned(
        info: MediaCodecInfo,
        codec: VideoCodec,
        size: PixelSize,
    ): PixelSize {
        val caps = videoCaps(info, codec)
        return PixelSize(
            size.width - size.width % caps.widthAlignment,
            size.height - size.height % caps.heightAlignment,
        )
    }

    /** True when this encoder accepts [size] at [fps] frames per second. */
    fun supports(
        info: MediaCodecInfo,
        codec: VideoCodec,
        size: PixelSize,
        fps: Int,
    ): Boolean {
        val caps = videoCaps(info, codec)
        return caps.isSizeSupported(size.width, size.height) &&
            caps.areSizeAndRateSupported(size.width, size.height, fps.toDouble())
    }

    private fun videoCaps(
        info: MediaCodecInfo,
        codec: VideoCodec,
    ) = checkNotNull(info.getCapabilitiesForType(codec.mime).videoCapabilities) { "${info.name} is not a video codec" }

    private fun acceptsSurface(
        info: MediaCodecInfo,
        codec: VideoCodec,
    ): Boolean =
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in info.getCapabilitiesForType(codec.mime).colorFormats
}
