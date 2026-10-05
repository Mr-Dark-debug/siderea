package io.github.mrdarkdebug.siderea.core.capture.timelapse

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** What the ramp may do. Shutter and ISO are the camera's own limits, already narrowed to the interval. */
data class RampLimits(
    val shutterMinNs: Long,
    val shutterMaxNs: Long,
    val isoMin: Int,
    val isoMax: Int,
    /** The most the total exposure may change between two frames, in stops. Small steps hide the change. */
    val maxStepStops: Double = DEFAULT_MAX_STEP,
) {
    init {
        require(shutterMinNs in 1..shutterMaxNs) { "shutter range is empty" }
        require(isoMin in 1..isoMax) { "ISO range is empty" }
        require(maxStepStops > 0) { "step must be positive" }
    }

    companion object {
        const val DEFAULT_MAX_STEP = 0.25
    }
}

/** The exposure to use for the next frame. */
data class RampExposure(
    val shutterNs: Long,
    val iso: Int,
    /** True when shutter and ISO are both at a limit and the scene still asks for more. */
    val limited: Boolean,
)

/**
 * Exposure ramping for day-to-night and night-to-day timelapses ("holy grail"). Instead of metering every frame
 * on its own, which makes brightness jump, the controller keeps one number: the total exposure in stops. After
 * every frame it compares that frame's brightness with the target and moves the total by a fraction of the error,
 * never more than [RampLimits.maxStepStops], so a sunset becomes a smooth change.
 *
 * The total is then split into shutter and ISO: the shutter takes as much as it can first (cleanest image),
 * ISO takes only what the shutter limit leaves over. Deflicker at export evens out what is left.
 */
class ExposureRamp(
    private val limits: RampLimits,
    startShutterNs: Long,
    startIso: Int,
    /** Brightness (0..1, encoded) to aim for. */
    private val targetLuma: Double = DEFAULT_TARGET,
    /** Brightens (positive) or darkens (negative) the whole sequence, in stops. */
    private val biasStops: Double = 0.0,
) {
    private val minTotal = totalStops(limits.shutterMinNs, limits.isoMin)
    private val maxTotal = totalStops(limits.shutterMaxNs, limits.isoMax)
    private var total = totalStops(startShutterNs, startIso).coerceIn(minTotal, maxTotal)

    /** The exposure for the next frame. */
    fun current(): RampExposure = split(total)

    /**
     * Feeds in the mean brightness (0..1, gamma-encoded) of the frame just taken and returns the exposure for the
     * next one.
     */
    fun update(meanLuma: Double): RampExposure {
        val measured = meanLuma.coerceIn(MIN_LUMA, MAX_LUMA)
        val errorStops = GAMMA * ln(targetLuma / measured) / LN2 + biasStops
        var pinned = false
        if (abs(errorStops) > DEAD_BAND_STOPS) {
            val move = (errorStops * DAMPING).coerceIn(-limits.maxStepStops, limits.maxStepStops)
            val wanted = total + move
            pinned = wanted < minTotal || wanted > maxTotal
            total = wanted.coerceIn(minTotal, maxTotal)
        }
        val next = split(total)
        // Still asking for more than the camera can give: shutter and ISO both sit at a limit.
        return if (pinned) next.copy(limited = true) else next
    }

    private fun totalStops(
        shutterNs: Long,
        iso: Int,
    ): Double = ln(shutterNs.toDouble() / NS_PER_SECOND * iso / limits.isoMin) / LN2

    private fun split(totalStops: Double): RampExposure {
        val wantedSeconds = 2.0.pow(totalStops)
        val shutterNs = (wantedSeconds * NS_PER_SECOND).toLong().coerceIn(limits.shutterMinNs, limits.shutterMaxNs)
        val shutterStops = ln(shutterNs.toDouble() / NS_PER_SECOND) / LN2
        val isoWanted = (limits.isoMin * 2.0.pow(totalStops - shutterStops)).roundToInt()
        val iso = isoWanted.coerceIn(limits.isoMin, limits.isoMax)
        val reached = totalStops(shutterNs, iso)
        return RampExposure(shutterNs, iso, limited = abs(reached - totalStops) > LIMIT_EPSILON)
    }

    companion object {
        const val DEFAULT_TARGET = 0.46
        private const val GAMMA = 2.2
        private const val DAMPING = 0.5
        private const val DEAD_BAND_STOPS = 0.1
        private const val MIN_LUMA = 0.005
        private const val MAX_LUMA = 0.995
        private const val LIMIT_EPSILON = 0.02
        private const val NS_PER_SECOND = 1_000_000_000.0
        private val LN2 = ln(2.0)
    }
}
