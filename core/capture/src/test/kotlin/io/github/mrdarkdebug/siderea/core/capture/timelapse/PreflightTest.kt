package io.github.mrdarkdebug.siderea.core.capture.timelapse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightTest {
    private val okInterval = IntervalCheck(true, 3_000, true, "Minimum interval: 3.0 s (measured)")

    private fun inputs(change: PreflightInputs.() -> PreflightInputs = { this }) =
        PreflightInputs(
            plannedFrames = 600,
            intervalMs = 10_000,
            sessionDurationMs = 6_000_000,
            bytesPerFrame = 4_000_000,
            freeBytes = 40_000_000_000,
            batteryPercent = 90,
            charging = false,
            estimatedBatteryPercent = 25.0,
            airplaneMode = true,
            manualFocus = true,
            exposureLocked = true,
            steady = true,
            intervalCheck = okInterval,
            notificationsAllowed = true,
            exactAlarmsAllowed = true,
            ignoringBatteryOptimisation = true,
            screenKeptOn = false,
        ).change()

    private fun item(
        id: String,
        change: PreflightInputs.() -> PreflightInputs,
    ) = Preflight.evaluate(inputs(change)).first { it.id == id }

    @Test
    fun `a well prepared session passes every check`() {
        val items = Preflight.evaluate(inputs())
        assertTrue(items.all { it.status == PreflightStatus.OK })
        assertTrue(Preflight.canStart(items))
        assertEquals(9, items.size)
    }

    @Test
    fun `a ramping exposure is fine even though it is not locked`() {
        val ramp = item("exposure") { copy(exposureLocked = false, exposureRamp = true) }
        assertEquals(PreflightStatus.OK, ramp.status)
        assertTrue(ramp.detail.contains("Ramping"))
    }

    @Test
    fun `an interval that is too short blocks the start`() {
        val bad = IntervalCheck(false, 8_700, true, "This interval is too short. Minimum interval: 8.7 s (measured).")
        val items = Preflight.evaluate(inputs { copy(intervalCheck = bad) })
        assertEquals(PreflightStatus.FAIL, items.first { it.id == "interval" }.status)
        assertFalse(Preflight.canStart(items))
    }

    @Test
    fun `not enough storage blocks the start and says how much is needed`() {
        val s = item("storage") { copy(freeBytes = 1_000_000_000) }
        assertEquals(PreflightStatus.FAIL, s.status)
        assertTrue(s.detail, s.detail.contains("2.9 GB needed but only 1.0 GB free"))
    }

    @Test
    fun `a session that runs until stopped does not need a storage total`() {
        assertEquals(PreflightStatus.OK, item("storage") { copy(plannedFrames = null) }.status)
    }

    @Test
    fun `low battery warns unless the phone is charging`() {
        assertEquals(PreflightStatus.WARN, item("battery") { copy(batteryPercent = 30) }.status)
        assertEquals(PreflightStatus.OK, item("battery") { copy(batteryPercent = 30, charging = true) }.status)
    }

    @Test
    fun `a moving phone, autofocus, auto exposure and wireless radios each produce a helpful warning`() {
        assertEquals(PreflightStatus.WARN, item("steady") { copy(steady = false) }.status)
        val focus = item("focus") { copy(manualFocus = false) }
        assertEquals(PreflightStatus.WARN, focus.status)
        assertEquals(PreflightFix.LOCK_FOCUS, focus.fix)
        assertEquals(PreflightStatus.WARN, item("exposure") { copy(exposureLocked = false) }.status)
        assertEquals(PreflightFix.AIRPLANE_MODE, item("airplane") { copy(airplaneMode = false) }.fix)
        assertEquals(PreflightFix.NOTIFICATIONS, item("notifications") { copy(notificationsAllowed = false) }.fix)
    }

    @Test
    fun `long gaps with the screen off ask for exact alarms`() {
        val b =
            item(
                "background",
            ) { copy(intervalMs = 1_800_000, exactAlarmsAllowed = false, ignoringBatteryOptimisation = false) }
        assertEquals(PreflightStatus.WARN, b.status)
        assertEquals(PreflightFix.EXACT_ALARMS, b.fix)
    }

    @Test
    fun `keeping the screen on makes background restrictions irrelevant`() {
        val b =
            item(
                "background",
            ) { copy(exactAlarmsAllowed = false, ignoringBatteryOptimisation = false, screenKeptOn = true) }
        assertEquals(PreflightStatus.OK, b.status)
    }

    @Test
    fun `warnings never block the start`() {
        val items = Preflight.evaluate(inputs { copy(steady = false, manualFocus = false, airplaneMode = false) })
        assertTrue(Preflight.canStart(items))
    }

    @Test
    fun `steadiness needs enough samples and a small spread`() {
        val d = MovementDetector(thresholdDegrees = 0.4f)
        assertNull(d.isSteady())
        (0 until 10).forEach { d.add(it * 100L, 0.05f * (it % 2), 10f) }
        assertEquals(true, d.isSteady())
        d.add(1_100, 3f, 10f)
        assertEquals(false, d.isSteady())
    }

    @Test
    fun `old samples fall out of the window`() {
        val d = MovementDetector(0.4f, windowMs = 1_000)
        (0 until 10).forEach { d.add(it * 100L, if (it == 0) 5f else 0f, 0f) }
        d.add(5_000, 0f, 0f)
        assertEquals("the early jolt is outside the window now", null, d.isSteady())
        (1 until 8).forEach { d.add(5_000 + it * 100L, 0f, 0f) }
        assertEquals(true, d.isSteady())
    }

    @Test
    fun `movement from a marked reference is detected in roll and elevation, across the wrap`() {
        val d = MovementDetector(1f)
        d.add(0, 179.5f, 20f)
        d.markReference()
        d.add(100, -179.8f, 20f) // 0.7 degrees away, across the +-180 seam
        assertFalse(d.movedFromReference())
        d.add(200, 179.5f, 22f)
        assertTrue(d.movedFromReference())
        assertFalse(MovementDetector(1f).movedFromReference())
    }
}
