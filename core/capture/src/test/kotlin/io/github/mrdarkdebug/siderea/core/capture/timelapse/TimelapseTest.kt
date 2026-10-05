package io.github.mrdarkdebug.siderea.core.capture.timelapse

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.capture.session.AppSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.CameraSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.DeviceSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.FrameRecord
import io.github.mrdarkdebug.siderea.core.capture.session.RequestedCapture
import io.github.mrdarkdebug.siderea.core.capture.session.SessionHandle
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SessionLayout
import io.github.mrdarkdebug.siderea.core.capture.session.SessionManifest
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStore
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TimelapseTest {
    @get:Rule
    val folder = TemporaryFolder()

    // ---- a virtual clock and fakes -----------------------------------------------------------------------

    private class VirtualClock : RunnerClock {
        var now = 0L

        override fun nowMs() = now

        override suspend fun delayUntil(targetMs: Long) {
            yield()
            if (targetMs > now) now = targetMs
        }
    }

    private class Notices : RunnerListener {
        val frames = mutableListOf<FrameRecord>()
        val texts = mutableListOf<String>()
        var lastOverhead: OverheadEstimate? = null

        override fun onFrame(
            record: FrameRecord,
            total: Int?,
            overhead: OverheadEstimate,
        ) {
            frames += record
            lastOverhead = overhead
        }

        override fun onNotice(
            text: String,
            warning: Boolean,
        ) {
            texts += text
        }
    }

    private val clock = VirtualClock()
    private val notices = Notices()
    private var inputs =
        GuardInputs(
            thermalStatus = 0,
            batteryPercent = 80,
            charging = false,
            freeBytes = 50_000_000_000,
            bytesPerFrame = 0,
        )

    private fun newSession(config: TimelapseConfig): SessionHandle {
        val store = SessionStore(folder.newFolder()) { clock.now }
        return store.create(SessionKind.TIMELAPSE) { id, created ->
            SessionManifest(
                id = id,
                name = "Test",
                kind = SessionKind.TIMELAPSE,
                status = SessionStatus.RUNNING,
                createdAtEpochMs = created,
                app = AppSnapshot("Siderea", "test", 1),
                device = DeviceSnapshot("Acme", "Test One", "16", 36),
                camera = CameraSnapshot("0", "0", null, "1x", "BACK"),
                requested = RequestedCapture(CaptureSettings(), "FOUR_THREE"),
                timelapse = config,
            )
        }
    }

    /** A capturer that takes [captureMs] of virtual time per frame and writes a record. */
    private fun capturer(
        captureMs: Long = 1_000,
        onFrame: (Int) -> FrameResult? = { null },
    ) = FrameCapturer { index, plannedAt, _ ->
        clock.now += captureMs
        onFrame(index) ?: FrameResult.Captured(
            FrameRecord(
                index = index,
                name = SessionLayout.frameName(index),
                plannedAtMs = plannedAt,
                capturedAtEpochMs = clock.now,
                exposureNs = 500_000_000,
                iso = 400,
                hasJpeg = true,
                jpegBytes = 4_000_000,
                captureMs = captureMs,
            ),
        )
    }

    private fun runner(
        config: TimelapseConfig,
        session: SessionHandle,
        capturer: FrameCapturer,
        guards: GuardSource = GuardSource { inputs.copy(bytesPerFrame = it) },
    ) = TimelapseRunner(config, session, CaptureFormat.JPEG, capturer, guards, clock, notices, epochMs = { clock.now })

    private val tenFrames = TimelapseConfig(10_000, StopCondition.FRAME_COUNT, frameCount = 10)

    // ---- the runner --------------------------------------------------------------------------------------

    @Test
    fun `it takes exactly the planned frames at an even cadence`() =
        runTest {
            val session = newSession(tenFrames)
            val result = runner(tenFrames, session, capturer()).run()
            assertEquals(RunResult.Completed(10), result)
            assertEquals((0 until 10).toList(), session.frames.map { it.index })
            assertEquals((0 until 10).map { it * 10_000L }, session.frames.map { it.plannedAtMs })
        }

    @Test
    fun `capture time does not accumulate into drift`() =
        runTest {
            val session = newSession(tenFrames)
            runner(tenFrames, session, capturer(captureMs = 3_000)).run()
            // Frame 9 is due at 90 s however long each capture took.
            assertEquals(90_000L, session.frames.last().plannedAtMs)
            assertEquals("no overrun notices expected", emptyList<String>(), notices.texts)
        }

    @Test
    fun `a capture that overruns its slot resets the schedule and says so`() =
        runTest {
            val config = TimelapseConfig(2_000, StopCondition.FRAME_COUNT, frameCount = 3)
            val session = newSession(config)
            runner(config, session, capturer(captureMs = 5_000)).run()
            assertTrue(notices.texts.any { it.contains("longer than the interval") })
            assertEquals(3, session.frames.size)
        }

    @Test
    fun `a duration limit becomes a frame count`() =
        runTest {
            val config = TimelapseConfig(5_000, StopCondition.DURATION, durationMs = 60_000)
            val session = newSession(config)
            assertEquals(RunResult.Completed(13), runner(config, session, capturer()).run())
        }

    @Test
    fun `an isolated failure is recorded and the session carries on`() =
        runTest {
            val session = newSession(tenFrames)
            val result =
                runner(
                    tenFrames,
                    session,
                    capturer { i ->
                        if (i ==
                            4
                        ) {
                            FrameResult.Failed("camera busy")
                        } else {
                            null
                        }
                    },
                ).run()
            assertEquals(RunResult.Completed(9), result)
            assertEquals("camera busy", session.frames.first { it.index == 4 }.error)
            assertEquals(9, session.usableFrames().size)
        }

    @Test
    fun `three failures in a row end the session and keep what was captured`() =
        runTest {
            val session = newSession(tenFrames)
            val result =
                runner(
                    tenFrames,
                    session,
                    capturer { i ->
                        if (i >=
                            5
                        ) {
                            FrameResult.Failed("sensor error")
                        } else {
                            null
                        }
                    },
                ).run()
            assertTrue(result is RunResult.Failed)
            assertEquals(5, result.frames)
            assertEquals(5, session.usableFrames().size)
        }

    @Test
    fun `a fatal failure ends the session at once`() =
        runTest {
            val session = newSession(tenFrames)
            val result =
                runner(
                    tenFrames,
                    session,
                    capturer { i ->
                        if (i ==
                            2
                        ) {
                            FrameResult.Failed("camera disconnected", fatal = true)
                        } else {
                            null
                        }
                    },
                ).run()
            assertTrue(result is RunResult.Failed)
            assertEquals(2, result.frames)
        }

    @Test
    fun `an overheating phone is paused, then resumes when it cools`() =
        runTest {
            val session = newSession(tenFrames)
            var checks = 0
            val guards =
                GuardSource {
                    checks++
                    inputs.copy(thermalStatus = if (checks in 4..5) 3 else 0, bytesPerFrame = it)
                }
            val result = runner(tenFrames, session, capturer(), guards).run()
            assertEquals(RunResult.Completed(10), result)
            assertTrue(notices.texts.any { it.contains("Pausing") })
        }

    @Test
    fun `a warm phone gets a longer interval and later returns to normal`() =
        runTest {
            val config = TimelapseConfig(10_000, StopCondition.FRAME_COUNT, frameCount = 8)
            val session = newSession(config)
            var checks = 0
            val guards = GuardSource { inputs.copy(thermalStatus = if (checks++ in 2..4) 2 else 0, bytesPerFrame = it) }
            runner(config, session, capturer(), guards).run()
            val gaps = session.frames.zipWithNext { a, b -> b.plannedAtMs - a.plannedAtMs }
            assertTrue("some gaps should be doubled: $gaps", gaps.any { it == 20_000L })
            assertEquals(10_000L, gaps.last())
            assertTrue(notices.texts.any { it.contains("warm") })
            assertTrue(notices.texts.any { it.contains("cooled down") })
        }

    @Test
    fun `a critically hot phone stops the session cleanly with everything kept`() =
        runTest {
            val session = newSession(tenFrames)
            var checks = 0
            val guards = GuardSource { inputs.copy(thermalStatus = if (checks++ >= 6) 4 else 0, bytesPerFrame = it) }
            val result = runner(tenFrames, session, capturer(), guards).run()
            assertTrue(result is RunResult.StoppedByGuard)
            assertEquals(GuardReason.HEAT, (result as RunResult.StoppedByGuard).reason)
            assertEquals(6, session.usableFrames().size)
        }

    @Test
    fun `a full disk stops the session before a frame is lost half written`() =
        runTest {
            val session = newSession(tenFrames)
            var checks = 0
            val guards =
                GuardSource {
                    inputs.copy(
                        freeBytes =
                            if (checks++ >=
                                3
                            ) {
                                50_000_000
                            } else {
                                50_000_000_000
                            },
                        bytesPerFrame = it,
                    )
                }
            val result = runner(tenFrames, session, capturer(), guards).run()
            assertTrue(result is RunResult.StoppedByGuard)
            assertEquals(GuardReason.STORAGE, (result as RunResult.StoppedByGuard).reason)
            assertEquals(3, session.usableFrames().size)
            assertTrue(notices.texts.any { it.contains("Storage is full") })
        }

    @Test
    fun `an empty battery stops the session`() =
        runTest {
            val session = newSession(tenFrames)
            var checks = 0
            val guards = GuardSource { inputs.copy(batteryPercent = if (checks++ >= 2) 2 else 60, bytesPerFrame = it) }
            assertTrue(runner(tenFrames, session, capturer(), guards).run() is RunResult.StoppedByGuard)
        }

    @Test
    fun `cancelling keeps every captured frame`() =
        runTest {
            val session = newSession(TimelapseConfig(10_000, StopCondition.UNTIL_STOPPED))
            val config = TimelapseConfig(10_000, StopCondition.UNTIL_STOPPED)
            var taken = 0
            val cancelling =
                capturer { i ->
                    taken++
                    if (i == 3) throw CancellationException("user stopped") else null
                }
            try {
                runner(config, session, cancelling).run()
            } catch (_: CancellationException) {
                // expected
            }
            assertEquals(3, session.usableFrames().size)
            assertEquals(4, taken)
        }

    @Test
    fun `resuming continues the numbering and the clock`() =
        runTest {
            val session = newSession(tenFrames)
            runner(tenFrames, session, capturer()).run(startIndex = 0)
            val resumed = newSession(tenFrames)
            val config = TimelapseConfig(10_000, StopCondition.FRAME_COUNT, frameCount = 10)
            val result = runner(config, resumed, capturer()).run(startIndex = 6, offsetMs = 60_000)
            assertEquals(RunResult.Completed(4), result)
            assertEquals(listOf(6, 7, 8, 9), resumed.frames.map { it.index })
            assertEquals(listOf(60_000L, 70_000L, 80_000L, 90_000L), resumed.frames.map { it.plannedAtMs })
        }

    @Test
    fun `measured overhead is learned from the frames`() =
        runTest {
            val session = newSession(tenFrames)
            runner(tenFrames, session, capturer(captureMs = 3_000)).run()
            val estimate = notices.lastOverhead!!
            assertTrue(estimate.measured)
            // 3 000 ms capture of a 500 ms exposure leaves 2 500 ms of overhead, plus the 15 % margin.
            assertEquals(2_875L, estimate.overheadMs)
        }

    // ---- interval maths ----------------------------------------------------------------------------------

    @Test
    fun `the minimum interval is exposure plus overhead plus a safety margin`() {
        val overhead = OverheadEstimate(7_450, measured = true, samples = 5)
        assertEquals(8_700L, IntervalMath.minimumIntervalMs(1_000_000_000, overhead))
    }

    @Test
    fun `a too-short interval says what the real minimum is`() {
        val overhead = OverheadEstimate(7_450, measured = true, samples = 5)
        val check = IntervalMath.check(5_000, 1_000_000_000, overhead)
        assertFalse(check.ok)
        assertTrue(check.message, check.message.contains("Minimum interval: 8.7 s (measured)"))
        assertTrue(IntervalMath.check(9_000, 1_000_000_000, overhead).ok)
    }

    @Test
    fun `an unmeasured overhead is labelled as an estimate`() {
        val check = IntervalMath.check(60_000, 1_000_000_000, OverheadTracker().estimate(CaptureFormat.RAW))
        assertFalse(check.measured)
        assertTrue(check.message.contains("estimated"))
    }

    @Test
    fun `raw frames start with a bigger overhead estimate than jpeg`() {
        val tracker = OverheadTracker()
        assertTrue(tracker.estimate(CaptureFormat.RAW).overheadMs > tracker.estimate(CaptureFormat.JPEG).overheadMs)
        assertEquals(0, tracker.estimate(CaptureFormat.JPEG).samples)
    }

    @Test
    fun `estimates for frames, video length, storage and battery`() {
        val config = TimelapseConfig(5_000, StopCondition.DURATION, durationMs = 3_600_000)
        assertEquals(721, IntervalMath.expectedFrames(config))
        assertEquals(24.033, IntervalMath.outputSeconds(721, 30), 0.001)
        assertEquals(2_884_000_000L, IntervalMath.storageBytes(721, 4_000_000))
        assertEquals(3_600_000L, IntervalMath.sessionDurationMs(config))
        assertEquals(90_000L, IntervalMath.sessionDurationMs(tenFrames))
        assertNull(IntervalMath.sessionDurationMs(TimelapseConfig(5_000, StopCondition.UNTIL_STOPPED)))
        // A camera that is busy 30 % of the time for an hour uses more than an idle one.
        val busy = IntervalMath.batteryPercent(3_600_000, 10_000, 500_000_000, 2_500)
        val idle = IntervalMath.batteryPercent(3_600_000, 600_000, 500_000_000, 2_500)
        assertTrue(busy > idle && idle >= 4.0)
    }

    @Test
    fun `typical frame sizes follow the format`() {
        val jpeg = IntervalMath.typicalFrameBytes(CaptureFormat.JPEG, 12f)
        val raw = IntervalMath.typicalFrameBytes(CaptureFormat.RAW, 12f)
        assertTrue(raw > jpeg)
        assertEquals(jpeg + raw, IntervalMath.typicalFrameBytes(CaptureFormat.RAW_JPEG, 12f))
    }

    @Test
    fun `seconds are formatted for people`() {
        assertEquals("8.7 s", IntervalMath.formatSeconds(8_700))
        assertEquals("2 min 5 s", IntervalMath.formatSeconds(125_000))
    }

    // ---- the guard policy --------------------------------------------------------------------------------

    private fun guard(
        adapt: Boolean = true,
        block: GuardInputs.() -> GuardInputs,
    ) = GuardPolicy.evaluate(GuardInputs(0, 80, false, 50_000_000_000, 5_000_000).block(), adapt)

    @Test
    fun `everything fine means carry on`() {
        assertEquals(GuardAction.CONTINUE, guard { this }.action)
    }

    @Test
    fun `heat escalates from warn to slow down to pause to stop`() {
        assertEquals(GuardAction.SLOW_DOWN, guard { copy(thermalStatus = 2) }.action)
        assertEquals(GuardAction.PAUSE, guard { copy(thermalStatus = 3) }.action)
        assertEquals(GuardAction.STOP, guard { copy(thermalStatus = 4) }.action)
        assertEquals(GuardAction.STOP, guard { copy(thermalStatus = 6) }.action)
    }

    @Test
    fun `without heat adaptation the user is warned instead of the interval changing`() {
        assertEquals(GuardAction.WARN, guard(adapt = false) { copy(thermalStatus = 2) }.action)
        assertEquals(GuardAction.WARN, guard(adapt = false) { copy(thermalStatus = 3) }.action)
        assertEquals(GuardAction.STOP, guard(adapt = false) { copy(thermalStatus = 4) }.action)
    }

    @Test
    fun `a low battery warns and a nearly flat one stops, unless it is charging`() {
        assertEquals(GuardAction.WARN, guard { copy(batteryPercent = 9) }.action)
        assertEquals(GuardAction.STOP, guard { copy(batteryPercent = 3) }.action)
        assertEquals(GuardAction.CONTINUE, guard { copy(batteryPercent = 1, charging = true) }.action)
    }

    @Test
    fun `storage warns under half a gigabyte and stops when a frame will not fit`() {
        assertEquals(GuardAction.WARN, guard { copy(freeBytes = 400_000_000) }.action)
        assertEquals(GuardAction.STOP, guard { copy(freeBytes = 100_000_000, bytesPerFrame = 30_000_000) }.action)
    }

    @Test
    fun `a stop reason beats a warning`() {
        val decision = guard { copy(batteryPercent = 9, freeBytes = 10_000_000) }
        assertEquals(GuardAction.STOP, decision.action)
        assertEquals(GuardReason.STORAGE, decision.reason)
    }

    @Test
    fun `unknown readings never stop a session`() {
        assertEquals(GuardAction.CONTINUE, GuardPolicy.evaluate(GuardInputs(null, null, false, null, 1), true).action)
    }
}
