package io.github.mrdarkdebug.siderea.core.capture

import io.github.mrdarkdebug.siderea.core.capture.timelapse.ExposureRamp
import io.github.mrdarkdebug.siderea.core.capture.timelapse.RampLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

class ExposureRampTest {
    private val limits =
        RampLimits(shutterMinNs = 125_000L, shutterMaxNs = 4_000_000_000L, isoMin = 100, isoMax = 1_600)

    /** A pretend camera: measured brightness from scene light and the exposure used, with clipping at white. */
    private fun measure(
        sceneLux: Double,
        shutterNs: Long,
        iso: Int,
    ): Double {
        val linear = (sceneLux * shutterNs / 1e9 * iso / 100.0).coerceIn(0.0, 1.0)
        return linear.pow(1 / 2.2)
    }

    private fun stops(
        shutterNs: Long,
        iso: Int,
    ) = ln(shutterNs / 1e9 * iso / 100.0) / ln(2.0)

    @Test
    fun aSteadySceneKeepsItsExposure() {
        // 0.46 encoded is 0.18 linear; a 1/100 s, ISO 100 frame at lux 18 sits there.
        val ramp = ExposureRamp(limits, 10_000_000L, 100)
        val start = ramp.current()
        repeat(20) { ramp.update(measure(18.0, ramp.current().shutterNs, ramp.current().iso)) }
        val end = ramp.current()
        assertTrue(abs(stops(end.shutterNs, end.iso) - stops(start.shutterNs, start.iso)) < 0.3)
    }

    @Test
    fun aSunsetIsFollowedSmoothlyAndStaysNearTheTarget() {
        // Light falls by 2^12 over 300 frames (a sunset's worth).
        val ramp = ExposureRamp(limits, 10_000_000L, 100)
        var previous = stops(ramp.current().shutterNs, ramp.current().iso)
        var worstStep = 0.0
        var worstError = 0.0
        for (frame in 0 until 300) {
            val lux = 18.0 * 2.0.pow(-12.0 * frame / 299.0)
            val e = ramp.current()
            val luma = measure(lux, e.shutterNs, e.iso)
            if (frame > 40) worstError = maxOf(worstError, abs(luma - 0.46))
            val next = ramp.update(luma)
            val now = stops(next.shutterNs, next.iso)
            worstStep = maxOf(worstStep, abs(now - previous))
            previous = now
        }
        assertTrue("exposure moved by up to $worstStep stops in one frame", worstStep <= 0.27)
        assertTrue("brightness strayed by $worstError", worstError < 0.12)
    }

    @Test
    fun aSunriseIsFollowedTheOtherWay() {
        val ramp = ExposureRamp(limits, 2_000_000_000L, 800)
        var worstError = 0.0
        for (frame in 0 until 300) {
            val lux = 0.005 * 2.0.pow(12.0 * frame / 299.0)
            val e = ramp.current()
            val luma = measure(lux, e.shutterNs, e.iso)
            if (frame > 60) worstError = maxOf(worstError, abs(luma - 0.46))
            ramp.update(luma)
        }
        assertTrue("brightness strayed by $worstError", worstError < 0.15)
    }

    @Test
    fun theShutterTakesTheLightFirstAndIsoOnlyWhatIsLeft() {
        val ramp = ExposureRamp(limits, 10_000_000L, 100)
        // Darkening world: the shutter should lengthen to its limit before ISO rises above the minimum.
        var sawIsoRiseBeforeShutterMax = false
        repeat(200) { frame ->
            val lux = 18.0 * 2.0.pow(-10.0 * frame / 199.0)
            val e = ramp.current()
            if (e.iso > limits.isoMin && e.shutterNs < limits.shutterMaxNs * 0.97) sawIsoRiseBeforeShutterMax = true
            ramp.update(measure(lux, e.shutterNs, e.iso))
        }
        assertFalse("ISO rose while the shutter still had room", sawIsoRiseBeforeShutterMax)
        assertTrue(ramp.current().iso > limits.isoMin)
    }

    @Test
    fun valuesNeverLeaveTheLimitsAndLimitedIsReported() {
        val ramp = ExposureRamp(limits, 10_000_000L, 100)
        var limited = false
        repeat(400) {
            val e = ramp.update(0.01) // permanently far too dark
            assertTrue(e.shutterNs in limits.shutterMinNs..limits.shutterMaxNs)
            assertTrue(e.iso in limits.isoMin..limits.isoMax)
            if (e.limited) limited = true
        }
        assertTrue("it must say when it cannot go any brighter", limited)
        assertEquals(limits.shutterMaxNs, ramp.current().shutterNs)
        assertEquals(limits.isoMax, ramp.current().iso)
    }

    @Test
    fun aBlownOutFrameCannotThrowTheExposureOffAtOnce() {
        val ramp = ExposureRamp(limits, 10_000_000L, 100)
        val before = ramp.current()
        val after = ramp.update(1.0)
        assertTrue(abs(stops(after.shutterNs, after.iso) - stops(before.shutterNs, before.iso)) <= 0.26)
    }

    @Test
    fun smallErrorsAreIgnoredSoItDoesNotHunt() {
        val ramp = ExposureRamp(limits, 10_000_000L, 100)
        val before = ramp.current()
        // About 0.05 stop of error is inside the dead band.
        val after = ramp.update(0.46 * 2.0.pow(0.05 / 2.2))
        assertEquals(before.shutterNs, after.shutterNs)
        assertEquals(before.iso, after.iso)
    }

    @Test
    fun biasShiftsTheWholeSequence() {
        fun settled(bias: Double): Double {
            val ramp = ExposureRamp(limits, 10_000_000L, 100, biasStops = bias)
            repeat(60) { ramp.update(measure(18.0, ramp.current().shutterNs, ramp.current().iso)) }
            return stops(ramp.current().shutterNs, ramp.current().iso)
        }
        assertTrue(settled(1.0) > settled(0.0) + 0.5)
        assertTrue(settled(-1.0) < settled(0.0) - 0.5)
    }

    @Test
    fun badLimitsAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { RampLimits(10, 5, 100, 200) }
        assertThrows(IllegalArgumentException::class.java) { RampLimits(5, 10, 400, 200) }
        assertThrows(IllegalArgumentException::class.java) { RampLimits(5, 10, 100, 200, maxStepStops = 0.0) }
    }
}
