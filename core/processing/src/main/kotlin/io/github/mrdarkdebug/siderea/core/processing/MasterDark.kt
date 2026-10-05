package io.github.mrdarkdebug.siderea.core.processing

/**
 * The average of several dark frames (same exposure and ISO, lens covered): the sensor's own hot pixels and
 * amplifier glow. Subtracting it from each light frame removes that pattern.
 *
 * Darks taken as JPEG have already been through the camera's noise reduction and tone curve, so subtracting them
 * is only an approximation; it helps most with hot pixels. RAW darks would be exact, which is why the real-world
 * gain is larger there.
 */
class MasterDark private constructor(
    val width: Int,
    val height: Int,
    private val red: ByteArray,
    private val green: ByteArray,
    private val blue: ByteArray,
    val frames: Int,
) {
    /** Subtracts the dark from [image] in place and returns it. [strength] 0..1 scales how much is taken off. */
    fun subtractFrom(
        image: RgbImage,
        strength: Float = 1f,
    ): RgbImage {
        require(image.width == width && image.height == height) { "Dark frame size differs from the lights" }
        for (i in image.pixels.indices) {
            val r = (image.red(i) - (red[i].toInt() and RgbImage.BYTE) * strength).toInt().coerceAtLeast(0)
            val g = (image.green(i) - (green[i].toInt() and RgbImage.BYTE) * strength).toInt().coerceAtLeast(0)
            val b = (image.blue(i) - (blue[i].toInt() and RgbImage.BYTE) * strength).toInt().coerceAtLeast(0)
            image.pixels[i] = RgbImage.pack(r, g, b)
        }
        return image
    }

    companion object {
        /** Averages every readable frame of [source]; null when none could be read. */
        fun average(
            source: FrameSource,
            checkCancelled: () -> Unit = {},
        ): MasterDark? {
            var sumR: IntArray? = null
            var sumG: IntArray? = null
            var sumB: IntArray? = null
            var width = 0
            var height = 0
            var used = 0
            for (index in 0 until source.count) {
                checkCancelled()
                val frame = source.load(index) ?: continue
                if (sumR == null) {
                    width = frame.width
                    height = frame.height
                    sumR = IntArray(width * height)
                    sumG = IntArray(width * height)
                    sumB = IntArray(width * height)
                } else if (frame.width != width || frame.height != height) {
                    continue
                }
                for (i in frame.pixels.indices) {
                    sumR[i] += frame.red(i)
                    sumG!![i] += frame.green(i)
                    sumB!![i] += frame.blue(i)
                }
                used++
            }
            if (sumR == null || used == 0) return null
            val r = ByteArray(width * height) { (sumR[it] / used).toByte() }
            val g = ByteArray(width * height) { (sumG!![it] / used).toByte() }
            val b = ByteArray(width * height) { (sumB!![it] / used).toByte() }
            return MasterDark(width, height, r, g, b, used)
        }
    }
}
