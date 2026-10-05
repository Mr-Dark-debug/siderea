package io.github.mrdarkdebug.siderea.core.export

import kotlin.math.exp
import kotlin.math.ln

/**
 * Evens out brightness flicker between frames. Each frame is compared with the average brightness of its
 * neighbours and given a gain that moves it onto that average. A slow change such as a sunset survives, because
 * the neighbours change slowly too; a single bright or dark frame does not.
 */
object Deflicker {
    private const val MIN_GAIN = 0.6
    private const val MAX_GAIN = 1.6
    private const val FLOOR = 1e-3
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE = 255
    private const val WEIGHT_R = 0.2126
    private const val WEIGHT_G = 0.7152
    private const val WEIGHT_B = 0.0722

    /** One gain per entry of [luma] (mean brightness 0..1 of each frame). [window] frames each side are averaged. */
    fun gains(
        luma: DoubleArray,
        window: Int,
    ): DoubleArray {
        if (window <= 0 || luma.size < 2) return DoubleArray(luma.size) { 1.0 }
        val logs = DoubleArray(luma.size) { ln(luma[it].coerceAtLeast(FLOOR)) }
        return DoubleArray(luma.size) { i ->
            val from = (i - window).coerceAtLeast(0)
            val to = (i + window).coerceAtMost(luma.lastIndex)
            var sum = 0.0
            for (j in from..to) sum += logs[j]
            val target = sum / (to - from + 1)
            exp(target - logs[i]).coerceIn(MIN_GAIN, MAX_GAIN)
        }
    }

    /** Mean brightness (0..1) of ARGB pixels using Rec. 709 weights on the encoded values. */
    fun meanLuma(pixels: IntArray): Double {
        if (pixels.isEmpty()) return 0.0
        var sum = 0.0
        for (p in pixels) {
            val r = (p shr RED_SHIFT) and BYTE
            val g = (p shr GREEN_SHIFT) and BYTE
            val b = p and BYTE
            sum += WEIGHT_R * r + WEIGHT_G * g + WEIGHT_B * b
        }
        return sum / pixels.size / BYTE
    }
}
