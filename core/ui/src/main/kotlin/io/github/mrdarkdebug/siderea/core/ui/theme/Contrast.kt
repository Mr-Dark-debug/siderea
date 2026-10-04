package io.github.mrdarkdebug.siderea.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.x contrast maths, kept out of Compose so it can be unit-tested and reused. */
object Contrast {
    /** Minimum ratio for body text (WCAG AA). */
    const val AA_TEXT = 4.5

    /** Minimum ratio for large text and UI components (WCAG AA). */
    const val AA_LARGE = 3.0

    fun ratio(
        a: Color,
        b: Color,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + FLARE) / (darker + FLARE)
    }

    fun relativeLuminance(color: Color): Double {
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        return LUMA_RED * r + LUMA_GREEN * g + LUMA_BLUE * b
    }

    private const val LUMA_RED = 0.2126
    private const val LUMA_GREEN = 0.7152
    private const val LUMA_BLUE = 0.0722
    private const val FLARE = 0.05
    private const val LINEAR_CUTOFF = 0.04045
    private const val LINEAR_SLOPE = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_SCALE = 1.055
    private const val GAMMA = 2.4

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= LINEAR_CUTOFF) c / LINEAR_SLOPE else ((c + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA)
    }
}
