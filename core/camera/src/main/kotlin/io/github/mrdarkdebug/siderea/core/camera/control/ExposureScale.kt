package io.github.mrdarkdebug.siderea.core.camera.control

import kotlin.math.abs
import kotlin.math.ln

/**
 * The familiar third-stop series for shutter, ISO and focus, restricted to what a lens supports.
 * The exact minimum and maximum a camera reports are always included, even when they fall between
 * two familiar values, so the user can reach the true limits (a 16 s exposure, ISO 7518).
 */
object ExposureScale {
    private const val NS_PER_SECOND = 1_000_000_000.0

    private val shutterSeconds =
        listOf(
            1.0 / 8000,
            1.0 / 6400,
            1.0 / 5000,
            1.0 / 4000,
            1.0 / 3200,
            1.0 / 2500,
            1.0 / 2000,
            1.0 / 1600,
            1.0 / 1250,
            1.0 / 1000,
            1.0 / 800,
            1.0 / 640,
            1.0 / 500,
            1.0 / 400,
            1.0 / 320,
            1.0 / 250,
            1.0 / 200,
            1.0 / 160,
            1.0 / 125,
            1.0 / 100,
            1.0 / 80,
            1.0 / 60,
            1.0 / 50,
            1.0 / 40,
            1.0 / 30,
            1.0 / 25,
            1.0 / 20,
            1.0 / 15,
            1.0 / 13,
            1.0 / 10,
            1.0 / 8,
            1.0 / 6,
            1.0 / 5,
            1.0 / 4,
            0.3,
            0.4,
            0.5,
            0.6,
            0.8,
            1.0,
            1.3,
            1.6,
            2.0,
            2.5,
            3.2,
            4.0,
            5.0,
            6.0,
            8.0,
            10.0,
            13.0,
            15.0,
            20.0,
            25.0,
            30.0,
            40.0,
            50.0,
            60.0,
        )

    private val isoStops =
        listOf(
            25,
            32,
            40,
            50,
            64,
            80,
            100,
            125,
            160,
            200,
            250,
            320,
            400,
            500,
            640,
            800,
            1000,
            1250,
            1600,
            2000,
            2500,
            3200,
            4000,
            5000,
            6400,
            8000,
            10000,
            12800,
            16000,
            20000,
            25600,
        )

    /**
     * Focus distances in diopters, ∞ first. Finer near infinity, where star focusing happens, and
     * coarser near the lens, where a small step is a large change.
     */
    private val focusDiopters =
        listOf(
            0f,
            0.02f,
            0.05f,
            0.1f,
            0.15f,
            0.2f,
            0.3f,
            0.4f,
            0.5f,
            0.65f,
            0.8f,
            1f,
            1.25f,
            1.5f,
            2f,
            2.5f,
            3f,
            4f,
            5f,
            6.5f,
            8f,
            10f,
            12.5f,
            16f,
            20f,
        )

    fun shutterStops(limits: ExposureLimits): List<Long> {
        val inRange =
            shutterSeconds
                .map { (it * NS_PER_SECOND).toLong() }
                .filter { it in limits.shutterMinNs..limits.shutterMaxNs }
        // Keep the true extremes reachable without crowding the list with near-duplicates.
        return withExtremes(inRange, limits.shutterMinNs, limits.shutterMaxNs, TOLERANCE_SHUTTER)
    }

    fun isoStops(limits: ExposureLimits): List<Int> {
        val inRange = isoStops.filter { it in limits.isoMin..limits.isoMax }
        return withExtremes(inRange.map(Int::toLong), limits.isoMin.toLong(), limits.isoMax.toLong(), TOLERANCE_ISO)
            .map(Long::toInt)
    }

    fun focusStops(limits: ExposureLimits): List<Float> {
        val inRange = focusDiopters.filter { it <= limits.focusNearDiopters }
        val last = inRange.last()
        return if (limits.focusNearDiopters - last > FOCUS_TOLERANCE) inRange + limits.focusNearDiopters else inRange
    }

    /** Index of the stop closest to [value] in log space, so 1/1000 is as far from 1/500 as 1/2000. */
    fun nearestShutterIndex(
        stops: List<Long>,
        ns: Long,
    ): Int = nearestLogIndex(stops.map(Long::toDouble), ns.toDouble())

    fun nearestIsoIndex(
        stops: List<Int>,
        iso: Int,
    ): Int = nearestLogIndex(stops.map(Int::toDouble), iso.toDouble())

    fun nearestFocusIndex(
        stops: List<Float>,
        diopters: Float,
    ): Int = stops.indices.minByOrNull { abs(stops[it] - diopters) } ?: 0

    /** Difference in stops: positive when [a] is a longer exposure / higher sensitivity than [b]. */
    fun stopsBetween(
        a: Double,
        b: Double,
    ): Double = ln(a / b) / LN2

    private fun nearestLogIndex(
        values: List<Double>,
        target: Double,
    ): Int = values.indices.minByOrNull { abs(ln(values[it] / target)) } ?: 0

    private fun withExtremes(
        values: List<Long>,
        min: Long,
        max: Long,
        relativeTolerance: Double,
    ): List<Long> {
        val result = values.toMutableList()
        if (result.isEmpty() || result.first() > min * (1 + relativeTolerance)) result.add(0, min)
        if (result.last() < max / (1 + relativeTolerance)) result.add(max)
        // A familiar stop that sits within tolerance of the true extreme is replaced by the exact value.
        if (result.first() != min && result.first() <= min * (1 + relativeTolerance)) result[0] = min
        if (result.last() != max && result.last() >= max / (1 + relativeTolerance)) result[result.lastIndex] = max
        return result.distinct()
    }

    private const val TOLERANCE_SHUTTER = 0.12
    private const val TOLERANCE_ISO = 0.12
    private const val FOCUS_TOLERANCE = 0.5f
    private val LN2 = ln(2.0)
}
