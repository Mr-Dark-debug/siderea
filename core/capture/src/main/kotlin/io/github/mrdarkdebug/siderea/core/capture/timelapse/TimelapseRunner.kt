package io.github.mrdarkdebug.siderea.core.capture.timelapse

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.capture.session.FrameRecord
import io.github.mrdarkdebug.siderea.core.capture.session.SessionHandle
import io.github.mrdarkdebug.siderea.core.capture.session.SessionLayout
import io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig

/** Takes one frame and writes its files. The real one drives the camera; tests use a fake. */
fun interface FrameCapturer {
    suspend fun capture(
        index: Int,
        plannedAtMs: Long,
        lockExposure: Boolean,
    ): FrameResult
}

sealed interface FrameResult {
    class Captured(
        val record: FrameRecord,
    ) : FrameResult

    /** [fatal] means retrying can't help (the camera is gone), so the session ends. */
    class Failed(
        val message: String,
        val fatal: Boolean = false,
    ) : FrameResult
}

/** Time as the runner sees it, so tests can run a night-long session in microseconds. */
interface RunnerClock {
    /** Monotonic milliseconds, for scheduling. Not wall-clock. */
    fun nowMs(): Long

    /** Waits until [targetMs] on the monotonic clock. Cancellable. */
    suspend fun delayUntil(targetMs: Long)
}

fun interface GuardSource {
    fun inputs(bytesPerFrame: Long): GuardInputs
}

/** What the runner reports as it goes. All calls come from the capture coroutine. */
interface RunnerListener {
    fun onFrame(
        record: FrameRecord,
        total: Int?,
        overhead: OverheadEstimate,
    )

    fun onNotice(
        text: String,
        warning: Boolean,
    )
}

sealed interface RunResult {
    val frames: Int

    data class Completed(
        override val frames: Int,
    ) : RunResult

    data class StoppedByGuard(
        override val frames: Int,
        val reason: GuardReason?,
        val message: String,
    ) : RunResult

    data class Failed(
        override val frames: Int,
        val message: String,
    ) : RunResult
}

/**
 * The intervalometer: waits for each frame's slot, checks the phone is fit to continue, captures, records
 * what really happened, and keeps the cadence honest.
 *
 * The schedule is absolute (frame n is due at start + n x interval), not "interval after the last frame",
 * so capture time does not accumulate into drift. If a capture overruns its slot the runner re-anchors and
 * says so, instead of silently producing an uneven timelapse.
 */
class TimelapseRunner(
    private val config: TimelapseConfig,
    private val session: SessionHandle,
    private val format: CaptureFormat,
    private val capturer: FrameCapturer,
    private val guards: GuardSource,
    private val clock: RunnerClock,
    private val listener: RunnerListener,
    private val epochMs: () -> Long = System::currentTimeMillis,
    private val overhead: OverheadTracker = OverheadTracker(),
) {
    private var largestFrameBytes = 0L
    private var lastWarning: String? = null
    private var slowedDown = false

    /**
     * @param startIndex index of the first frame to take (non-zero when resuming).
     * @param offsetMs how much of the session had already elapsed before this run, so frame times stay
     *        continuous across a resume.
     */
    suspend fun run(
        startIndex: Int = 0,
        offsetMs: Long = 0,
    ): RunResult {
        val planned = config.plannedFrames
        val startMs = clock.nowMs()
        var index = startIndex
        var nextAtMs = startMs
        var failures = 0

        while (planned == null || index < planned) {
            clock.delayUntil(nextAtMs)
            val stop = awaitFitToContinue()
            if (stop != null) return stop

            val plannedAt = offsetMs + (nextAtMs - startMs)
            val result = capturer.capture(index, plannedAt, config.lockExposure)
            val captured = (result as? FrameResult.Captured)?.record
            if (captured != null) {
                failures = 0
                recordFrame(captured, planned)
            } else {
                val failed = result as FrameResult.Failed
                failures++
                recordFailure(index, plannedAt, failed.message)
                if (failed.fatal || failures >= MAX_CONSECUTIVE_FAILURES) {
                    return RunResult.Failed(session.frames.count { it.error == null }, failed.message)
                }
            }
            index++
            nextAtMs = nextSlot(nextAtMs)
        }
        return RunResult.Completed(session.frames.count { it.error == null })
    }

    /** Keeps the absolute schedule, but re-anchors with a notice when a frame overran its slot. */
    private fun nextSlot(previousSlot: Long): Long {
        val interval = currentIntervalMs()
        val due = previousSlot + interval
        val now = clock.nowMs()
        if (now <= due) return due
        val late = now - due
        notice(
            "Frame took ${late / MS_PER_SECOND_I} s longer than the interval; the schedule was reset.",
            warning = true,
        )
        return now
    }

    private fun currentIntervalMs(): Long =
        if (slowedDown) (config.intervalMs * SLOW_FACTOR).toLong() else config.intervalMs

    /** Returns a result when the session must end; waits out a cool-down pause otherwise. */
    private suspend fun awaitFitToContinue(): RunResult? {
        var pausedMs = 0L
        while (true) {
            val decision = GuardPolicy.evaluate(guards.inputs(largestFrameBytes), config.adaptToHeat)
            when (decision.action) {
                GuardAction.CONTINUE -> {
                    if (slowedDown) {
                        slowedDown = false
                        notice("The phone has cooled down. Back to the normal interval.", warning = false)
                    }
                }

                GuardAction.WARN -> {
                    warnOnce(decision.message)
                }

                GuardAction.SLOW_DOWN -> {
                    if (!slowedDown) {
                        slowedDown = true
                        notice(decision.message.orEmpty(), warning = true)
                    }
                }

                GuardAction.PAUSE -> {
                    if (pausedMs == 0L) notice(decision.message.orEmpty(), warning = true)
                    if (pausedMs >= MAX_PAUSE_MS) {
                        return stopByGuard(decision, "The phone didn't cool down in time. Siderea stopped.")
                    }
                    clock.delayUntil(clock.nowMs() + decision.pauseMs)
                    pausedMs += decision.pauseMs
                }

                GuardAction.STOP -> {
                    return stopByGuard(decision, decision.message.orEmpty())
                }
            }
            if (decision.action != GuardAction.PAUSE) return null
        }
    }

    private fun stopByGuard(
        decision: GuardDecision,
        message: String,
    ): RunResult {
        notice(message, warning = true)
        return RunResult.StoppedByGuard(session.frames.count { it.error == null }, decision.reason, message)
    }

    private fun recordFrame(
        record: FrameRecord,
        planned: Int?,
    ) {
        session.appendFrame(record)
        largestFrameBytes = maxOf(largestFrameBytes, record.jpegBytes + record.dngBytes)
        record.exposureNs?.let { overhead.record(record.captureMs, it) }
        listener.onFrame(record, planned, overhead.estimate(format))
    }

    private fun recordFailure(
        index: Int,
        plannedAt: Long,
        message: String,
    ) {
        val record =
            FrameRecord(
                index = index,
                name = SessionLayout.frameName(index),
                plannedAtMs = plannedAt,
                capturedAtEpochMs = epochMs(),
                error = message,
            )
        session.appendFrame(record)
        notice("Frame ${index + 1} failed: $message", warning = true)
    }

    private fun warnOnce(message: String?) {
        if (message != null && message != lastWarning) {
            lastWarning = message
            notice(message, warning = true)
        }
    }

    private fun notice(
        text: String,
        warning: Boolean,
    ) {
        if (text.isBlank()) return
        session.addEvent(text)
        listener.onNotice(text, warning)
    }

    private companion object {
        const val MAX_CONSECUTIVE_FAILURES = 3
        const val MAX_PAUSE_MS = 30 * 60_000L
        const val SLOW_FACTOR = 2.0
        const val MS_PER_SECOND_I = 1_000L
    }
}
