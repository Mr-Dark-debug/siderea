package io.github.mrdarkdebug.siderea.core.camera.capability

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

/** Turns focal lengths into the 0.5x / 1x / 5x labels photographers expect. */
object ZoomLabels {
    /** The focal length, in 35 mm terms, that phones treat as "the main camera". */
    private const val MAIN_LENS_TARGET_MM = 26.0

    /**
     * 35 mm-equivalent focal length: how wide the lens looks compared with a full-frame sensor.
     * Returns null when the sensor size is not reported.
     */
    fun equivalentFocalLengthMm(
        focalMm: Float,
        sensorWidthMm: Float?,
        sensorHeightMm: Float?,
    ): Float? {
        if (!isPositive(sensorWidthMm) || !isPositive(sensorHeightMm)) return null
        val sensorDiagonal = hypot(sensorWidthMm!!.toDouble(), sensorHeightMm!!.toDouble())
        return (focalMm * CameraFormat.fullFrameDiagonalMm / sensorDiagonal).toFloat()
    }

    private fun isPositive(value: Float?) = value != null && value > 0f

    /**
     * Picks the main lens among cameras facing the same way: the one whose equivalent focal length is
     * closest (in log space, so 13 mm and 52 mm are equally far from 26 mm) to a typical main lens.
     * Ties go to the earlier candidate.
     */
    fun pickMain(candidates: List<Pair<String, Float>>): String? =
        candidates.minByOrNull { (_, equivalent) -> abs(ln(equivalent / MAIN_LENS_TARGET_MM)) }?.first

    /**
     * Formats a zoom ratio the way camera apps do. Sub-1x rounds to 0.1 (0.5x), 1x to 3x rounds to
     * 0.5 (1x, 1.5x, 2.5x) and anything longer rounds to a whole number (5x, 10x).
     */
    fun format(ratio: Float): String {
        require(ratio > 0f) { "ratio must be positive" }
        val rounded =
            when {
                ratio < 1f -> (ratio * 10f).roundToInt() / 10f
                ratio < 3f -> (ratio * 2f).roundToInt() / 2f
                else -> ratio.roundToInt().toFloat()
            }
        val text =
            if (rounded == rounded.toInt().toFloat()) {
                rounded.toInt().toString()
            } else {
                String.format(Locale.ROOT, "%.1f", rounded)
            }
        return "${text}x"
    }
}
