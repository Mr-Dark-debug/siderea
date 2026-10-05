package io.github.mrdarkdebug.siderea.core.capture.timelapse

/** What the phone is doing: heat, battery and free space, sampled before each frame. */
data class GuardInputs(
    /** `PowerManager.THERMAL_STATUS_*`: 0 none, 1 light, 2 moderate, 3 severe, 4 critical, 5 emergency, 6 shutdown. */
    val thermalStatus: Int?,
    val batteryPercent: Int?,
    val charging: Boolean,
    val freeBytes: Long?,
    /** Size of the largest recent frame, used to decide whether another one will fit. */
    val bytesPerFrame: Long,
)

enum class GuardAction {
    CONTINUE,

    /** Carry on, but tell the user. */
    WARN,

    /** Carry on with a longer interval so the phone can cool. */
    SLOW_DOWN,

    /** Wait for the phone to cool before the next frame. */
    PAUSE,

    /** End the session cleanly and keep everything captured. */
    STOP,
}

data class GuardDecision(
    val action: GuardAction,
    /** Plain-language explanation, shown in the notification and stored in the session. */
    val message: String?,
    /** For [GuardAction.SLOW_DOWN]: multiply the interval by this. */
    val intervalFactor: Double = 1.0,
    /** For [GuardAction.PAUSE]: how long to wait. */
    val pauseMs: Long = 0,
    val reason: GuardReason? = null,
) {
    companion object {
        val OK = GuardDecision(GuardAction.CONTINUE, null)
    }
}

enum class GuardReason { HEAT, BATTERY, STORAGE }

/**
 * Decides, before every frame, whether to carry on. The aim is that a long session never dies silently:
 * it warns, slows down or pauses first, and when it must stop it does so cleanly with everything kept.
 */
object GuardPolicy {
    private const val THERMAL_MODERATE = 2
    private const val THERMAL_SEVERE = 3
    private const val THERMAL_CRITICAL = 4
    private const val BATTERY_WARN = 10
    private const val BATTERY_STOP = 3
    private const val STORAGE_WARN_BYTES = 500_000_000L
    private const val STORAGE_RESERVE_BYTES = 100_000_000L
    private const val SLOW_FACTOR = 2.0
    private const val COOL_DOWN_MS = 60_000L

    fun evaluate(
        inputs: GuardInputs,
        adaptToHeat: Boolean,
    ): GuardDecision {
        storage(inputs)?.let { if (it.action == GuardAction.STOP) return it }
        battery(inputs)?.let { if (it.action == GuardAction.STOP) return it }
        heat(inputs, adaptToHeat)?.let { return it }
        return storage(inputs) ?: battery(inputs) ?: GuardDecision.OK
    }

    private fun heat(
        inputs: GuardInputs,
        adaptToHeat: Boolean,
    ): GuardDecision? {
        val status = inputs.thermalStatus ?: return null
        return when {
            status >= THERMAL_CRITICAL -> {
                GuardDecision(
                    GuardAction.STOP,
                    "The phone is overheating (thermal level $status). Siderea stopped and kept every frame so far.",
                    reason = GuardReason.HEAT,
                )
            }

            status >= THERMAL_SEVERE && adaptToHeat -> {
                GuardDecision(
                    GuardAction.PAUSE,
                    "The phone is very hot. Pausing for a minute to cool down.",
                    pauseMs = COOL_DOWN_MS,
                    reason = GuardReason.HEAT,
                )
            }

            status >= THERMAL_SEVERE -> {
                GuardDecision(
                    GuardAction.WARN,
                    "The phone is very hot and may shut down. Shade it or stop the session.",
                    reason = GuardReason.HEAT,
                )
            }

            status >= THERMAL_MODERATE && adaptToHeat -> {
                GuardDecision(
                    GuardAction.SLOW_DOWN,
                    "The phone is warm. Frames are being spaced further apart to let it cool.",
                    intervalFactor = SLOW_FACTOR,
                    reason = GuardReason.HEAT,
                )
            }

            status >= THERMAL_MODERATE -> {
                GuardDecision(
                    GuardAction.WARN,
                    "The phone is warm. Consider shading it.",
                    reason = GuardReason.HEAT,
                )
            }

            else -> {
                null
            }
        }
    }

    private fun battery(inputs: GuardInputs): GuardDecision? {
        val percent = inputs.batteryPercent ?: return null
        if (inputs.charging) return null
        return when {
            percent <= BATTERY_STOP -> {
                GuardDecision(
                    GuardAction.STOP,
                    "The battery is at $percent %. Siderea stopped and kept every frame so far.",
                    reason = GuardReason.BATTERY,
                )
            }

            percent <= BATTERY_WARN -> {
                GuardDecision(
                    GuardAction.WARN,
                    "The battery is at $percent % and not charging. " +
                        "Plug in or the session will stop at $BATTERY_STOP %.",
                    reason = GuardReason.BATTERY,
                )
            }

            else -> {
                null
            }
        }
    }

    private fun storage(inputs: GuardInputs): GuardDecision? {
        val free = inputs.freeBytes ?: return null
        val needed = inputs.bytesPerFrame * 2 + STORAGE_RESERVE_BYTES
        return when {
            free < needed -> {
                GuardDecision(
                    GuardAction.STOP,
                    "Storage is full. Siderea stopped and kept every frame so far.",
                    reason = GuardReason.STORAGE,
                )
            }

            free < STORAGE_WARN_BYTES -> {
                GuardDecision(
                    GuardAction.WARN,
                    "Less than 500 MB of storage is left.",
                    reason = GuardReason.STORAGE,
                )
            }

            else -> {
                null
            }
        }
    }
}
