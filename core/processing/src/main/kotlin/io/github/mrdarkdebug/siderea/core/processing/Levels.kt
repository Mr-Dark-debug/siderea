@file:Suppress("MagicNumber") // Percentiles and the default gamma.

package io.github.mrdarkdebug.siderea.core.processing

import kotlin.math.pow

/** A levels curve on 0..255 values: everything at or below [black] goes black, at or above [white] goes white. */
data class Levels(
    val black: Float,
    val white: Float,
    val gamma: Float,
) {
    fun map(value: Float): Float {
        val t = ((value - black) / (white - black)).coerceIn(0f, 1f)
        return t.pow(1f / gamma) * 255f
    }

    /** Applies the curve to every pixel of [image], returning a new image. */
    fun apply(image: RgbImage): RgbImage {
        val lut = IntArray(256) { (map(it.toFloat()) + 0.5f).toInt().coerceIn(0, 255) }
        val out = IntArray(image.pixels.size)
        for (i in out.indices) out[i] = RgbImage.pack(lut[image.red(i)], lut[image.green(i)], lut[image.blue(i)])
        return RgbImage(image.width, image.height, out)
    }

    companion object {
        val NONE = Levels(0f, 255f, 1f)

        /**
         * Picks a black point just under the sky background and a white point just under the brightest stars, then
         * lifts the midtones. Without it a stack of dark-sky frames looks almost black.
         */
        fun automatic(
            image: RgbImage,
            lowPercentile: Double = 0.5,
            highPercentile: Double = 99.95,
            gamma: Float = 1.8f,
        ): Levels {
            val histogram = IntArray(256)
            for (i in image.pixels.indices) histogram[image.luma(i).toInt().coerceIn(0, 255)]++
            val total = image.pixels.size.toLong()
            val low = percentile(histogram, total, lowPercentile)
            val high = percentile(histogram, total, highPercentile).coerceAtLeast(low + 8)
            return Levels(low.toFloat(), high.toFloat(), gamma)
        }

        private fun percentile(
            histogram: IntArray,
            total: Long,
            percent: Double,
        ): Int {
            val target = total * percent / 100.0
            var seen = 0L
            for (v in histogram.indices) {
                seen += histogram[v]
                if (seen >= target) return v
            }
            return histogram.lastIndex
        }
    }
}
