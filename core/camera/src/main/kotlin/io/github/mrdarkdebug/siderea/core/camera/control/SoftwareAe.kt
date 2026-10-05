package io.github.mrdarkdebug.siderea.core.camera.control

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Shutter-priority and ISO-priority auto-exposure, in software.
 *
 * The user fixes one of shutter or ISO and this controller moves the other until the preview's mean
 * brightness reaches a target. It runs on the viewfinder analysis, a few times a second, with damping so
 * it settles instead of hunting.
 */
object SoftwareAe {
    /** Mean display luma the controller aims for (about 46 % of full scale, a normal mid-grey exposure). */
    const val TARGET_LUMA = 118f

    /** Within this distance of the target nothing changes, to stop flicker. */
    const val DEAD_BAND = 8f

    /** Display luma is gamma-encoded; this converts an error in luma to an error in linear light. */
    private const val GAMMA = 2.2

    private const val DAMPING = 0.6
    private const val MAX_STEP_STOPS = 1.0
    private val LN2 = ln(2.0)

    class Step(
        val shutterNs: Long,
        val iso: Int,
        /** True when the free value is pinned at its limit and still can't reach the target. */
        val limited: Boolean,
    )

    /**
     * @param shutterPriority true to keep the shutter and move the ISO; false to keep the ISO and move
     *        the shutter.
     * @param bias exposure bias in stops from the user's compensation (positive = brighter).
     */
    fun step(
        meanLuma: Float,
        shutterNs: Long,
        iso: Int,
        shutterPriority: Boolean,
        limits: ExposureLimits,
        bias: Float = 0f,
    ): Step {
        val target = TARGET_LUMA * 2.0.pow(bias.toDouble() / GAMMA)
        val clamped = meanLuma.coerceAtLeast(1f)
        if (abs(clamped - target) <= DEAD_BAND) return Step(shutterNs, iso, limited = false)

        val errorStops = GAMMA * ln(target / clamped) / LN2
        val moveStops = (errorStops * DAMPING).coerceIn(-MAX_STEP_STOPS, MAX_STEP_STOPS)
        val factor = 2.0.pow(moveStops)

        return if (shutterPriority) {
            val wanted = (iso * factor).toInt().coerceAtLeast(1)
            val newIso = limits.clampIso(wanted)
            val limited =
                newIso != wanted &&
                    ((moveStops > 0 && newIso == limits.isoMax) || (moveStops < 0 && newIso == limits.isoMin))
            Step(shutterNs, newIso, limited)
        } else {
            val wanted = (shutterNs * factor).toLong().coerceAtLeast(1L)
            val newShutter = limits.clampShutter(wanted)
            val limited =
                newShutter != wanted && (
                    (moveStops > 0 && newShutter == limits.shutterMaxNs) ||
                        (moveStops < 0 && newShutter == limits.shutterMinNs)
                )
            Step(newShutter, iso, limited)
        }
    }
}
