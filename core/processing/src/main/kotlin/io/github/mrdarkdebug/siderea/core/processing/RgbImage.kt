package io.github.mrdarkdebug.siderea.core.processing

/** An opaque 8-bit RGB image packed as ARGB ints, row by row. Plain arrays so every algorithm runs on the JVM. */
class RgbImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray = IntArray(width * height),
) {
    init {
        require(width > 0 && height > 0) { "Empty image" }
        require(pixels.size == width * height) { "Pixel array does not match ${width}x$height" }
    }

    fun red(index: Int): Int = (pixels[index] shr RED_SHIFT) and BYTE

    fun green(index: Int): Int = (pixels[index] shr GREEN_SHIFT) and BYTE

    fun blue(index: Int): Int = pixels[index] and BYTE

    /** Brightness 0..255 of one pixel, Rec. 709 weights on the encoded values. */
    fun luma(index: Int): Float = LUMA_R * red(index) + LUMA_G * green(index) + LUMA_B * blue(index)

    companion object {
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BYTE = 0xFF
        const val OPAQUE = 0xFF000000.toInt()
        private const val LUMA_R = 0.2126f
        private const val LUMA_G = 0.7152f
        private const val LUMA_B = 0.0722f

        fun pack(
            r: Int,
            g: Int,
            b: Int,
        ): Int = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    }
}

/** Where the frames of a session come from. Implementations decode lazily so only one frame is in memory. */
interface FrameSource {
    val count: Int

    /** The frame at [index], or null when it cannot be read (a damaged file). All frames share one size. */
    fun load(index: Int): RgbImage?
}
