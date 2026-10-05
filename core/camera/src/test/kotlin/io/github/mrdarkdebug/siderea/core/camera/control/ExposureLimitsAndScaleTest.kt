package io.github.mrdarkdebug.siderea.core.camera.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExposureLimitsAndScaleTest {
    private val main = Pixel10Fixture.limits("0")

    @Test
    fun `limits come straight from the real camera`() {
        assertTrue(main.manualExposure)
        assertEquals(56_968L, main.shutterMinNs)
        assertEquals(16_000_002_085L, main.shutterMaxNs)
        assertEquals(30, main.isoMin)
        assertEquals(7518, main.isoMax)
        assertEquals(-4f, main.evMinStops, 0.01f)
        assertEquals(4f, main.evMaxStops, 0.01f)
        assertTrue(main.raw)
    }

    @Test
    fun `manual focus works on the main lens with an assumed near limit and is off on the ultra-wide`() {
        assertTrue(main.manualFocus)
        assertTrue(main.focusNearIsAssumed)
        assertEquals(ExposureLimits.ASSUMED_NEAR_DIOPTERS, main.focusNearDiopters, 0f)
        val ultraWide = Pixel10Fixture.limits("3")
        assertFalse(ultraWide.manualFocus)
        assertTrue(ultraWide.manualFocusReason!!.contains("fixed-focus"))
    }

    @Test
    fun `front camera is limited to one second`() {
        assertEquals(1_000_000_932L, Pixel10Fixture.limits("1").shutterMaxNs)
    }

    @Test
    fun `clamps keep values inside the lens range`() {
        assertEquals(main.shutterMinNs, main.clampShutter(1))
        assertEquals(main.shutterMaxNs, main.clampShutter(Long.MAX_VALUE))
        assertEquals(30, main.clampIso(1))
        assertEquals(7518, main.clampIso(99_999))
        assertEquals(0f, main.clampFocus(-3f), 0f)
        assertEquals(10f, main.clampFocus(99f), 0f)
        assertEquals(4f, main.clampEv(9f), 0.01f)
    }

    @Test
    fun `a camera without manual sensor is not manual and says why`() {
        val basic =
            ExposureLimits.from(
                Pixel10Fixture.camera("0").copy(manualSensor = false, capabilities = listOf("BACKWARD_COMPATIBLE")),
                37,
            )
        assertFalse(basic.manualExposure)
        assertTrue(basic.manualExposureReason!!.contains("MANUAL_SENSOR"))
    }

    @Test
    fun `shutter stops are increasing, in range and reach the true extremes`() {
        val stops = ExposureScale.shutterStops(main)
        assertEquals(main.shutterMinNs, stops.first())
        assertEquals(main.shutterMaxNs, stops.last())
        assertEquals(stops.sorted(), stops)
        assertEquals(stops.distinct(), stops)
        assertTrue(stops.any { abs(it - 8_000_000L) < 100_000 })
    }

    @Test
    fun `iso stops include the exact limits and the familiar values between`() {
        val stops = ExposureScale.isoStops(main)
        assertEquals(30, stops.first())
        assertEquals(7518, stops.last())
        assertTrue(stops.containsAll(listOf(100, 200, 400, 800, 1600, 3200)))
        assertEquals(stops.sorted(), stops)
    }

    @Test
    fun `focus stops start at infinity and stop at the near limit`() {
        val stops = ExposureScale.focusStops(main)
        assertEquals(0f, stops.first(), 0f)
        assertEquals(10f, stops.last(), 0f)
        assertEquals(stops.sorted(), stops)
    }

    @Test
    fun `nearest index works in log space`() {
        val stops = listOf(1_000L, 2_000L, 4_000L)
        assertEquals(1, ExposureScale.nearestShutterIndex(stops, 2_700L))
        assertEquals(2, ExposureScale.nearestShutterIndex(stops, 3_000L))
        assertEquals(0, ExposureScale.nearestIsoIndex(listOf(100, 200), 120))
        assertEquals(3, ExposureScale.nearestFocusIndex(listOf(0f, 0.1f, 0.2f, 0.5f), 0.4f))
    }

    @Test
    fun `stops between two values`() {
        assertEquals(1.0, ExposureScale.stopsBetween(2.0, 1.0), 1e-9)
        assertEquals(-3.0, ExposureScale.stopsBetween(1.0, 8.0), 1e-9)
    }

    @Test
    fun `preview keeps a live frame rate under a long manual shutter`() {
        val preview = PreviewExposure.forManual(shutterNs = 16_000_000_000L, iso = 800, limits = main)
        assertEquals(PreviewExposure.MAX_PREVIEW_SHUTTER_NS, preview.shutterNs)
        assertEquals(main.isoMax, preview.iso)
        // The sensor can't deliver all of it, so the display makes up the rest.
        assertTrue(preview.displayGain > 6f && preview.displayGain <= PreviewExposure.MAX_DISPLAY_GAIN)
    }

    @Test
    fun `preview raises iso instead of gain when it can`() {
        val preview = PreviewExposure.forManual(shutterNs = 1_000_000_000L, iso = 100, limits = main)
        assertEquals(250_000_000L, preview.shutterNs)
        assertEquals(400, preview.iso)
        assertEquals(1f, preview.displayGain, 0.01f)
    }

    @Test
    fun `fast shutters are previewed exactly as chosen`() {
        val preview = PreviewExposure.forManual(shutterNs = 4_000_000L, iso = 200, limits = main)
        assertEquals(PreviewExposure(4_000_000L, 200, 1f), preview)
    }
}
