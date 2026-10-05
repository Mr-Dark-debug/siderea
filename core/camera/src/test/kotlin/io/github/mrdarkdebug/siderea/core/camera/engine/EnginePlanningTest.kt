package io.github.mrdarkdebug.siderea.core.camera.engine

import io.github.mrdarkdebug.siderea.core.camera.capability.LensFacing
import io.github.mrdarkdebug.siderea.core.camera.capability.SizeInfo
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.camera.control.Pixel10Fixture
import io.github.mrdarkdebug.siderea.core.camera.control.PreviewExposure
import io.github.mrdarkdebug.siderea.core.camera.control.SensorCalibration
import io.github.mrdarkdebug.siderea.core.camera.control.WbMode
import io.github.mrdarkdebug.siderea.core.camera.control.WhiteBalance
import io.github.mrdarkdebug.siderea.core.camera.control.WhiteBalanceMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePlanningTest {
    private val lenses = LensCatalog.from(Pixel10Fixture.report)
    private val main: ExposureLimits = Pixel10Fixture.limits("0")
    private val caps =
        EngineCapabilities(
            afModes = setOf("OFF", "AUTO", "CONTINUOUS_PICTURE"),
            awbModes = setOf("OFF", "AUTO", "DAYLIGHT", "INCANDESCENT", "CLOUDY_DAYLIGHT"),
            cct = false,
            calibration = SensorCalibration(21, WhiteBalanceMath.XYZ_TO_SRGB, 17, WhiteBalanceMath.XYZ_TO_SRGB),
        )

    private fun plan(
        settings: CaptureSettings,
        forPreview: Boolean,
        limits: ExposureLimits = main,
    ) = RequestPlanner.plan(settings, settings.shutterNs, settings.iso, limits, caps, forPreview)

    // ---- lens catalog ------------------------------------------------------------------------------------

    @Test
    fun `the pixel 10 offers exactly its five real lenses, ordered by facing then zoom`() {
        assertEquals(
            listOf("BACK 0.6x", "BACK 1x", "BACK 5x", "FRONT 0.9x", "FRONT 1x"),
            lenses.map { "${it.facing} ${it.zoomLabel}" },
        )
    }

    @Test
    fun `the default lens of each logical camera uses the logical camera directly`() {
        val back = lenses.first { it.facing == LensFacing.BACK && it.zoomLabel == "1x" }
        assertEquals("0", back.openId)
        assertNull(back.physicalId)
        val front = lenses.first { it.facing == LensFacing.FRONT && it.zoomLabel == "1x" }
        assertEquals("1", front.openId)
        assertNull(front.physicalId)
    }

    @Test
    fun `other lenses are bound to their physical camera`() {
        val ultraWide = lenses.first { it.zoomLabel == "0.6x" }
        assertEquals("0", ultraWide.openId)
        assertEquals("3", ultraWide.physicalId)
        assertEquals("4", lenses.first { it.zoomLabel == "5x" }.physicalId)
        assertEquals("5", lenses.first { it.zoomLabel == "0.9x" }.physicalId)
        assertNotNull(ultraWide.logical)
    }

    @Test
    fun `the default lens is the main back camera`() {
        val default = LensCatalog.default(lenses)!!
        assertEquals(LensFacing.BACK, default.facing)
        assertEquals("1x", default.zoomLabel)
    }

    @Test
    fun `lens keys are unique`() {
        assertEquals(lenses.size, lenses.map { it.key }.toSet().size)
    }

    // ---- request planning --------------------------------------------------------------------------------

    @Test
    fun `automatic exposure turns evStops into camera steps`() {
        val p = plan(CaptureSettings(exposureMode = ExposureMode.AUTO, evStops = 1f), forPreview = true)
        assertTrue(p.autoExposure)
        assertEquals(6, p.exposureCompensationSteps)
        assertEquals(1f, p.displayGain, 0f)
    }

    @Test
    fun `compensation is clamped to what the camera allows`() {
        assertEquals(24, RequestPlanner.evSteps(99f, main))
        assertEquals(-24, RequestPlanner.evSteps(-99f, main))
    }

    @Test
    fun `a manual sixteen second photo uses the full exposure and a frame duration that allows it`() {
        val settings = CaptureSettings(exposureMode = ExposureMode.MANUAL, shutterNs = 16_000_000_000L, iso = 800)
        val still = plan(settings, forPreview = false)
        assertFalse(still.autoExposure)
        assertEquals(16_000_000_000L, still.shutterNs)
        assertEquals(800, still.iso)
        assertTrue(still.frameDurationNs >= still.shutterNs)
        assertTrue(still.frameDurationNs <= main.maxFrameDurationNs)
    }

    @Test
    fun `the same setting previews at a capped shutter with display gain`() {
        val settings = CaptureSettings(exposureMode = ExposureMode.MANUAL, shutterNs = 16_000_000_000L, iso = 800)
        val live = plan(settings, forPreview = true)
        assertEquals(PreviewExposure.MAX_PREVIEW_SHUTTER_NS, live.shutterNs)
        assertTrue(live.displayGain > 1f)
        assertTrue(live.frameDurationNs >= live.shutterNs)
    }

    @Test
    fun `fast shutters ask for a normal frame rate`() {
        val p =
            plan(
                CaptureSettings(exposureMode = ExposureMode.MANUAL, shutterNs = 1_000_000L, iso = 100),
                forPreview = true,
            )
        assertEquals(33_333_333L, p.frameDurationNs)
    }

    @Test
    fun `out of range values are clamped`() {
        val p =
            plan(
                CaptureSettings(exposureMode = ExposureMode.MANUAL, shutterNs = 1L, iso = 999_999),
                forPreview = false,
            )
        assertEquals(main.shutterMinNs, p.shutterNs)
        assertEquals(main.isoMax, p.iso)
    }

    @Test
    fun `a lens without manual exposure stays automatic whatever was asked`() {
        val basic =
            ExposureLimits.from(
                Pixel10Fixture.camera("0").copy(manualSensor = false, capabilities = listOf("BACKWARD_COMPATIBLE")),
                37,
            )
        val p = plan(CaptureSettings(exposureMode = ExposureMode.MANUAL), forPreview = false, limits = basic)
        assertTrue(p.autoExposure)
    }

    @Test
    fun `software priority modes run the sensor manually with the effective values`() {
        val settings = CaptureSettings(exposureMode = ExposureMode.SHUTTER_PRIORITY, shutterNs = 8_333_333L)
        val p = RequestPlanner.plan(settings, 8_333_333L, 640, main, caps, forPreview = false)
        assertFalse(p.autoExposure)
        assertEquals(640, p.iso)
    }

    @Test
    fun `manual focus is applied only on lenses that can focus`() {
        val manual = CaptureSettings(focusMode = FocusMode.MANUAL, focusDiopters = 0.2f)
        val p = plan(manual, forPreview = true)
        assertEquals("OFF", p.afMode)
        assertEquals(0.2f, p.focusDiopters!!, 0f)
        val ultraWide = plan(manual, forPreview = true, limits = Pixel10Fixture.limits("3"))
        assertNull(ultraWide.focusDiopters)
    }

    @Test
    fun `automatic focus prefers continuous picture`() {
        assertEquals("CONTINUOUS_PICTURE", plan(CaptureSettings(), true).afMode)
        val onlyAuto = EngineCapabilities(setOf("OFF", "AUTO"), emptySet(), false, null)
        val p = RequestPlanner.plan(CaptureSettings(), 1, 100, main, onlyAuto, true)
        assertEquals("AUTO", p.afMode)
    }

    @Test
    fun `white balance presets use the camera's own mode when it has one`() {
        val p = plan(CaptureSettings(whiteBalance = WhiteBalance(WbMode.DAYLIGHT)), true)
        assertEquals(WbPlan.Preset("DAYLIGHT"), p.wb)
    }

    @Test
    fun `a missing preset is computed from the sensor calibration instead`() {
        val p = plan(CaptureSettings(whiteBalance = WhiteBalance(WbMode.SHADE)), true)
        assertTrue(p.wb is WbPlan.Gains)
    }

    @Test
    fun `kelvin becomes gains and a matrix when the camera has no cct control`() {
        val p = plan(CaptureSettings(whiteBalance = WhiteBalance(WbMode.KELVIN, 3200, 10)), true)
        val gains = p.wb as WbPlan.Gains
        assertTrue(gains.calibrated)
        assertEquals(4, gains.rggb.size)
        assertEquals(18, gains.transformRationals.size)
        assertTrue(gains.rggb[0] < 1f && gains.rggb[3] > 1f)
    }

    @Test
    fun `kelvin uses the android 16 control when the camera offers it`() {
        val cctCaps = EngineCapabilities(caps.afModes, caps.awbModes, cct = true, calibration = caps.calibration)
        val p =
            RequestPlanner.plan(
                CaptureSettings(whiteBalance = WhiteBalance(WbMode.KELVIN, 4500, -20)),
                1,
                100,
                main,
                cctCaps,
                true,
            )
        assertEquals(WbPlan.Cct(4500, -20), p.wb)
    }

    // ---- sizing ------------------------------------------------------------------------------------------

    @Test
    fun `capture size follows the chosen shape`() {
        val sizes = Pixel10Fixture.camera("0").outputs.jpegSizes
        assertEquals(SizeInfo(4000, 3000), SensorSizing.capture(sizes, AspectRatio.FOUR_THREE))
        assertEquals(SizeInfo(4000, 2256), SensorSizing.capture(sizes, AspectRatio.SIXTEEN_NINE))
    }

    @Test
    fun `raw sizes follow the shape too`() {
        val raw = Pixel10Fixture.camera("0").outputs.rawSizes
        assertEquals(SizeInfo(4000, 3000), SensorSizing.capture(raw, AspectRatio.FOUR_THREE))
        assertEquals(SizeInfo(4000, 2256), SensorSizing.capture(raw, AspectRatio.SIXTEEN_NINE))
    }

    @Test
    fun `a lens with no size of the wanted shape falls back to its largest`() {
        assertEquals(SizeInfo(3440, 2448), SensorSizing.capture(listOf(SizeInfo(3440, 2448)), AspectRatio.SIXTEEN_NINE))
        assertNull(SensorSizing.capture(emptyList(), AspectRatio.FOUR_THREE))
    }

    @Test
    fun `preview stays small and matches the shape`() {
        val sizes = listOf(SizeInfo(4000, 3000), SizeInfo(1920, 1440), SizeInfo(1920, 1080), SizeInfo(1280, 960))
        assertEquals(SizeInfo(1920, 1440), SensorSizing.preview(sizes, AspectRatio.FOUR_THREE))
        assertEquals(SizeInfo(1920, 1080), SensorSizing.preview(sizes, AspectRatio.SIXTEEN_NINE))
    }

    @Test
    fun `taps map from the upright preview to the sensor`() {
        // Upright top-left is the sensor's bottom-left corner when the sensor is mounted at 90 degrees.
        assertEquals(0f to 1f, SensorSizing.tapToSensor(0f, 0f, 90))
        assertEquals(1f to 0f, SensorSizing.tapToSensor(0f, 0f, 270))
        assertEquals(0.5f to 0.5f, SensorSizing.tapToSensor(0.5f, 0.5f, 90))
        assertEquals(0.75f to 0.75f, SensorSizing.tapToSensor(0.25f, 0.25f, 180))
        assertEquals(0.3f to 0.6f, SensorSizing.tapToSensor(0.3f, 0.6f, 0))
        val (x, y) = SensorSizing.tapToSensor(0.9f, 0.2f, 90)
        assertEquals(0.2f, x, 1e-6f)
        assertEquals(0.1f, y, 1e-6f)
    }
}
