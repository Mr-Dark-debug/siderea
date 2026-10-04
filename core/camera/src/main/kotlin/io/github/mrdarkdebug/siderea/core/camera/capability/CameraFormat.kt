package io.github.mrdarkdebug.siderea.core.camera.capability

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Pure display and unit helpers. No Android dependencies, so everything here is unit-tested. */
object CameraFormat {
    private const val NS_PER_SECOND = 1_000_000_000.0
    private const val FRACTION_CUTOFF_SECONDS = 0.5
    private const val WHOLE_SECONDS_FROM = 10.0
    private const val MIN_FPS_FOR_ONE_DECIMAL = 1.0
    private const val SLOW_FPS_DECIMALS = 3

    /** Nanoseconds to a photographer's shutter label: `1/8000 s`, `0.8 s`, `30 s`. */
    fun exposure(ns: Long): String {
        val seconds = ns / NS_PER_SECOND
        return when {
            seconds <= 0.0 -> "0 s"
            seconds <= FRACTION_CUTOFF_SECONDS -> "1/${(1.0 / seconds).roundToInt()} s"
            seconds < WHOLE_SECONDS_FROM -> "${trimmed(seconds, 1)} s"
            else -> "${trimmed(seconds, 0)} s"
        }
    }

    fun exposureSeconds(ns: Long): Double = ns / NS_PER_SECOND

    /** Frame duration (ns) to the equivalent frame rate, e.g. `30 fps` or `0.03 fps`. */
    fun frameRate(frameDurationNs: Long): String {
        if (frameDurationNs <= 0) return "n/a"
        val fps = NS_PER_SECOND / frameDurationNs
        return if (fps >=
            MIN_FPS_FOR_ONE_DECIMAL
        ) {
            "${trimmed(fps, 1)} fps"
        } else {
            "${trimmed(fps, SLOW_FPS_DECIMALS)} fps"
        }
    }

    /** Diopters of focus distance to metres; null for infinity (0 dpt). */
    fun diopterToMeters(diopters: Float): Float? = if (diopters <= 0f) null else 1f / diopters

    /** `12 cm`, `1.2 m`, or `∞`. */
    fun distance(meters: Float?): String =
        when {
            meters == null -> "∞"
            meters < 1f -> "${(meters * CM_PER_METER).roundToInt()} cm"
            else -> "${trimmed(meters.toDouble(), 1)} m"
        }

    fun megapixels(
        width: Int,
        height: Int,
    ): Float = width.toLong().times(height) / MEGA

    fun size(size: SizeInfo): String = "${size.width}×${size.height}"

    /** A number with at most [decimals] decimals and no trailing zeros: `12.93` -> `12.9`, `1.0` -> `1`. */
    fun number(
        value: Float,
        decimals: Int,
    ): String = trimmed(value.toDouble(), decimals)

    fun focal(mm: Float): String = "${trimmed(mm.toDouble(), 1)} mm"

    fun aperture(f: Float): String = "f/${trimmed(f.toDouble(), 1)}"

    /** Signed exposure-compensation value, in EV. */
    fun ev(
        steps: Int,
        stepSize: Double,
    ): String {
        val ev = steps * stepSize
        val text = trimmed(abs(ev), 1)
        return when {
            ev > 0.0 -> "+$text EV"
            ev < 0.0 -> "−$text EV"
            else -> "0 EV"
        }
    }

    /** Diagonal of a 36 x 24 mm full-frame sensor, the basis of "35 mm equivalent". */
    val fullFrameDiagonalMm: Double = hypot(36.0, 24.0)

    private const val CM_PER_METER = 100f
    private const val MEGA = 1_000_000f

    private fun trimmed(
        value: Double,
        decimals: Int,
    ): String {
        val text = String.format(Locale.ROOT, "%.${decimals}f", value)
        return if (decimals == 0 || !text.contains('.')) text else text.trimEnd('0').trimEnd('.')
    }
}
