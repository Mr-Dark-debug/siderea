package io.github.mrdarkdebug.siderea.core.export

import kotlin.math.min
import kotlin.math.roundToInt

/** Pure geometry and sizing for video export, so it can be tested without an encoder. */
object ExportMath {
    private const val MAX_BITRATE = 100_000_000
    private const val MIN_BITRATE = 500_000
    private const val EVEN = 2
    private const val BITS_PER_BYTE = 8.0
    private const val SHRINK = 0.8
    private const val MIN_SIDE = 64

    /** The part of a [srcWidth] x [srcHeight] frame to keep for [crop], positioned by [panX] / [panY]. */
    fun cropRect(
        srcWidth: Int,
        srcHeight: Int,
        crop: CropAspect,
        panX: Float = VideoSpec.CENTRE,
        panY: Float = VideoSpec.CENTRE,
    ): PixelRect {
        val aw = crop.width
        val ah = crop.height
        if (aw == null || ah == null) return PixelRect(0, 0, evenDown(srcWidth), evenDown(srcHeight))
        val target = aw.toDouble() / ah
        val source = srcWidth.toDouble() / srcHeight
        val cropW: Int
        val cropH: Int
        if (source > target) {
            cropH = evenDown(srcHeight)
            cropW = min(evenDown((cropH * target).roundToInt()), evenDown(srcWidth))
        } else {
            cropW = evenDown(srcWidth)
            cropH = min(evenDown((cropW / target).roundToInt()), evenDown(srcHeight))
        }
        val left = evenDown(((srcWidth - cropW) * panX.coerceIn(0f, 1f)).roundToInt())
        val top = evenDown(((srcHeight - cropH) * panY.coerceIn(0f, 1f)).roundToInt())
        return PixelRect(left, top, left + cropW, top + cropH)
    }

    /** The output frame size: the crop scaled so its long side fits [size], never enlarged, always even. */
    fun outputSize(
        cropWidth: Int,
        cropHeight: Int,
        size: OutputSize,
    ): PixelSize {
        val limit = size.longSide ?: return PixelSize(evenDown(cropWidth), evenDown(cropHeight))
        val longSide = maxOf(cropWidth, cropHeight)
        if (longSide <= limit) return PixelSize(evenDown(cropWidth), evenDown(cropHeight))
        val scale = limit.toDouble() / longSide
        return PixelSize(
            evenDown((cropWidth * scale).roundToInt()),
            evenDown((cropHeight * scale).roundToInt()),
        )
    }

    fun bitrate(
        size: PixelSize,
        spec: VideoSpec,
    ): Int {
        val raw = size.width.toDouble() * size.height * spec.fps * spec.codec.bitsPerPixel * spec.quality.factor
        return raw.toInt().coerceIn(MIN_BITRATE, MAX_BITRATE)
    }

    fun videoSeconds(
        frames: Int,
        fps: Int,
    ): Double = if (fps <= 0) 0.0 else frames.toDouble() / fps

    fun estimatedBytes(
        frames: Int,
        size: PixelSize,
        spec: VideoSpec,
    ): Long = (bitrate(size, spec) / BITS_PER_BYTE * videoSeconds(frames, spec.fps)).toLong()

    /**
     * Shrinks [size] in even steps until [fits] accepts it, keeping the aspect ratio. Returns null when even
     * the smallest step is refused.
     */
    fun fitToEncoder(
        size: PixelSize,
        fits: (PixelSize) -> Boolean,
    ): PixelSize? {
        var current = size
        while (current.width >= MIN_SIDE && current.height >= MIN_SIDE) {
            if (fits(current)) return current
            current =
                PixelSize(
                    evenDown((current.width * SHRINK).roundToInt()),
                    evenDown((current.height * SHRINK).roundToInt()),
                )
        }
        return null
    }

    private fun evenDown(value: Int): Int = value - value % EVEN
}
