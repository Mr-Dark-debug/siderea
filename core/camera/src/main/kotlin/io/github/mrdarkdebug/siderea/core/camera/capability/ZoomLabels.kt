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
    private const val FOUR_THIRDS = 4.0 / 3.0
    private const val TENTH_STEPS = 10f
    private const val HALF_STEPS = 2f
    private const val LONG_FROM = 3f

    /**
     * 35 mm-equivalent focal length for the **4:3 stills Siderea shoots**.
     *
     * Many sensors are not exactly 4:3 (a Pixel 10 telephoto is 3976 x 2736), and a 4:3 photo crops the
     * long side. Using the full sensor diagonal would make that lens look wider than the photo is, and
     * the zoom label would disagree with the stock camera app (4.3x instead of 5x). So the diagonal of the
     * 4:3 crop is used. Returns null when the sensor size is not reported.
     */
    fun equivalentFocalLengthMm(
        focalMm: Float,
        sensorWidthMm: Float?,
        sensorHeightMm: Float?,
    ): Float? {
        if (!isPositive(sensorWidthMm) || !isPositive(sensorHeightMm)) return null
        val width = sensorWidthMm!!.toDouble()
        val height = sensorHeightMm!!.toDouble()
        val cropWidth = minOf(width, height * FOUR_THIRDS)
        val cropHeight = minOf(height, width / FOUR_THIRDS)
        val cropDiagonal = hypot(cropWidth, cropHeight)
        return (focalMm * CameraFormat.fullFrameDiagonalMm / cropDiagonal).toFloat()
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
                ratio < 1f -> (ratio * TENTH_STEPS).roundToInt() / TENTH_STEPS
                ratio < LONG_FROM -> (ratio * HALF_STEPS).roundToInt() / HALF_STEPS
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
