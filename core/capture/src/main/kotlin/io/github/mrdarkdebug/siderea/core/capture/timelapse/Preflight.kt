package io.github.mrdarkdebug.siderea.core.capture.timelapse

import kotlin.math.abs

enum class PreflightStatus { OK, WARN, FAIL }

/** What the user can do about an item. The UI turns this into a button. */
enum class PreflightFix { NONE, NOTIFICATIONS, EXACT_ALARMS, BATTERY_OPTIMISATION, AIRPLANE_MODE, LOCK_FOCUS }

data class PreflightItem(
    val id: String,
    val title: String,
    val status: PreflightStatus,
    val detail: String,
    val fix: PreflightFix = PreflightFix.NONE,
)

/** Everything the checklist looks at. Gathered by the app, judged here. */
data class PreflightInputs(
    val plannedFrames: Int?,
    val intervalMs: Long,
    val sessionDurationMs: Long?,
    val bytesPerFrame: Long,
    val freeBytes: Long?,
    val batteryPercent: Int?,
    val charging: Boolean,
    val estimatedBatteryPercent: Double?,
    val airplaneMode: Boolean?,
    val manualFocus: Boolean,
    val exposureLocked: Boolean,
    val steady: Boolean?,
    val intervalCheck: IntervalCheck,
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val ignoringBatteryOptimisation: Boolean,
    val screenKeptOn: Boolean,
)

/** The pre-flight checklist shown before a long session starts. */
object Preflight {
    private const val SHORT_INTERVAL_MS = 60_000L
    private const val STORAGE_HEADROOM = 1.2
    private const val BATTERY_MARGIN = 10

    fun evaluate(i: PreflightInputs): List<PreflightItem> =
        listOf(
            interval(i),
            storage(i),
            battery(i),
            steady(i),
            focus(i),
            exposure(i),
            airplane(i),
            notifications(i),
            background(i),
        )

    /** True when nothing blocks the start (warnings don't). */
    fun canStart(items: List<PreflightItem>): Boolean = items.none { it.status == PreflightStatus.FAIL }

    private fun interval(i: PreflightInputs) =
        if (i.intervalCheck.ok) {
            PreflightItem("interval", "Interval", PreflightStatus.OK, i.intervalCheck.message)
        } else {
            PreflightItem("interval", "Interval", PreflightStatus.FAIL, i.intervalCheck.message)
        }

    private fun storage(i: PreflightInputs): PreflightItem {
        val free =
            i.freeBytes ?: return PreflightItem("storage", "Storage", PreflightStatus.WARN, "Couldn't read free space.")
        val frames =
            i.plannedFrames
                ?: return PreflightItem(
                    "storage",
                    "Storage",
                    PreflightStatus.OK,
                    "Runs until stopped; Siderea stops cleanly if space runs out.",
                )
        val needed = (frames * i.bytesPerFrame * STORAGE_HEADROOM).toLong()
        return if (free >= needed) {
            PreflightItem("storage", "Storage", PreflightStatus.OK, "About ${gb(needed)} needed, ${gb(free)} free.")
        } else {
            PreflightItem(
                "storage",
                "Storage",
                PreflightStatus.FAIL,
                "About ${gb(needed)} needed but only ${gb(free)} free. Free up space or shorten the session.",
            )
        }
    }

    private fun battery(i: PreflightInputs): PreflightItem {
        val level =
            i.batteryPercent
                ?: return PreflightItem("battery", "Battery", PreflightStatus.WARN, "Couldn't read the battery.")
        if (i.charging) return PreflightItem("battery", "Battery", PreflightStatus.OK, "Charging ($level %).")
        val needed = i.estimatedBatteryPercent
        return when {
            needed != null && level < needed + BATTERY_MARGIN -> {
                PreflightItem(
                    "battery",
                    "Battery",
                    PreflightStatus.WARN,
                    "At $level % with about ${needed.toInt()} % needed. Plug in a power bank for a safe margin.",
                )
            }

            else -> {
                PreflightItem("battery", "Battery", PreflightStatus.OK, "$level %.")
            }
        }
    }

    private fun steady(i: PreflightInputs) =
        when (i.steady) {
            true -> {
                PreflightItem("steady", "Tripod steady", PreflightStatus.OK, "The phone isn't moving.")
            }

            false -> {
                PreflightItem(
                    "steady",
                    "Tripod steady",
                    PreflightStatus.WARN,
                    "The phone is moving. Rest it on a tripod or a firm surface.",
                )
            }

            null -> {
                PreflightItem(
                    "steady",
                    "Tripod steady",
                    PreflightStatus.WARN,
                    "No motion sensor, so steadiness can't be checked.",
                )
            }
        }

    private fun focus(i: PreflightInputs) =
        if (i.manualFocus) {
            PreflightItem("focus", "Focus locked", PreflightStatus.OK, "Manual focus: it won't hunt between frames.")
        } else {
            PreflightItem(
                "focus",
                "Focus locked",
                PreflightStatus.WARN,
                "Autofocus can shift between frames and make the video pulse. Lock focus manually.",
                PreflightFix.LOCK_FOCUS,
            )
        }

    private fun exposure(i: PreflightInputs) =
        if (i.exposureLocked) {
            PreflightItem("exposure", "Exposure", PreflightStatus.OK, "Locked for every frame.")
        } else {
            PreflightItem(
                "exposure",
                "Exposure",
                PreflightStatus.WARN,
                "Exposure is metered per frame, which can flicker. Fine for sunsets; for a steady scene lock it.",
            )
        }

    private fun airplane(i: PreflightInputs) =
        when (i.airplaneMode) {
            true -> {
                PreflightItem(
                    "airplane",
                    "Airplane mode",
                    PreflightStatus.OK,
                    "On: no interruptions, less battery use.",
                )
            }

            else -> {
                PreflightItem(
                    "airplane",
                    "Airplane mode",
                    PreflightStatus.WARN,
                    "Turning it on avoids calls and notifications spoiling a frame, and saves battery.",
                    PreflightFix.AIRPLANE_MODE,
                )
            }
        }

    private fun notifications(i: PreflightInputs) =
        if (i.notificationsAllowed) {
            PreflightItem("notifications", "Progress notification", PreflightStatus.OK, "Allowed.")
        } else {
            PreflightItem(
                "notifications",
                "Progress notification",
                PreflightStatus.WARN,
                "Without it you can't see progress or stop the session from the notification shade.",
                PreflightFix.NOTIFICATIONS,
            )
        }

    private fun background(i: PreflightInputs): PreflightItem {
        val longGaps = i.intervalMs >= SHORT_INTERVAL_MS
        return when {
            i.screenKeptOn -> {
                PreflightItem(
                    "background",
                    "Runs with the screen off",
                    PreflightStatus.OK,
                    "The screen stays on (dimmed), so Android won't put the phone to deep sleep.",
                )
            }

            i.exactAlarmsAllowed && i.ignoringBatteryOptimisation -> {
                PreflightItem(
                    "background",
                    "Runs with the screen off",
                    PreflightStatus.OK,
                    "Exact alarms and battery exemption are allowed.",
                )
            }

            longGaps && !i.exactAlarmsAllowed -> {
                PreflightItem(
                    "background",
                    "Runs with the screen off",
                    PreflightStatus.WARN,
                    "Allow exact alarms so long gaps between frames stay on time while the phone sleeps.",
                    PreflightFix.EXACT_ALARMS,
                )
            }

            else -> {
                PreflightItem(
                    "background",
                    "Runs with the screen off",
                    PreflightStatus.WARN,
                    "Android may sleep the phone between frames. Keep the screen on (dimmed) " +
                        "or exempt Siderea from battery optimisation.",
                    PreflightFix.BATTERY_OPTIMISATION,
                )
            }
        }
    }

    private fun gb(bytes: Long) = "%.1f GB".format(java.util.Locale.ROOT, bytes / GIGA)

    private const val GIGA = 1_000_000_000.0
}

/**
 * Tracks how still the phone is. Used for the "tripod steady" check, and during astro capture to flag
 * frames taken while the phone was bumped.
 */
class MovementDetector(
    private val thresholdDegrees: Float,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {
    private data class Sample(
        val atMs: Long,
        val roll: Float,
        val elevation: Float,
    )

    private val samples = ArrayDeque<Sample>()
    private var reference: Pair<Float, Float>? = null

    fun add(
        nowMs: Long,
        rollDegrees: Float,
        elevationDegrees: Float,
    ) {
        samples.addLast(Sample(nowMs, rollDegrees, elevationDegrees))
        while (samples.isNotEmpty() && nowMs - samples.first().atMs > windowMs) samples.removeFirst()
    }

    /** True when the recent window shows less movement than the threshold; null with too little data. */
    fun isSteady(): Boolean? {
        if (samples.size < MIN_SAMPLES) return null
        val rollSpread = samples.maxOf { it.roll } - samples.minOf { it.roll }
        val elevationSpread = samples.maxOf { it.elevation } - samples.minOf { it.elevation }
        return maxOf(rollSpread, elevationSpread) <= thresholdDegrees
    }

    /** Remembers the current pose as "where the camera should stay". */
    fun markReference() {
        samples.lastOrNull()?.let { reference = it.roll to it.elevation }
    }

    /** True when the latest reading differs from the reference by more than the threshold. */
    fun movedFromReference(): Boolean {
        val ref = reference ?: return false
        val now = samples.lastOrNull() ?: return false
        return abs(angleDifference(now.roll, ref.first)) > thresholdDegrees ||
            abs(now.elevation - ref.second) > thresholdDegrees
    }

    private fun angleDifference(
        a: Float,
        b: Float,
    ): Float {
        var d = (a - b) % FULL_TURN
        if (d > HALF_TURN) d -= FULL_TURN
        if (d < -HALF_TURN) d += FULL_TURN
        return d
    }

    private companion object {
        const val DEFAULT_WINDOW_MS = 3_000L
        const val MIN_SAMPLES = 5
        const val FULL_TURN = 360f
        const val HALF_TURN = 180f
    }
}
