package io.github.mrdarkdebug.siderea.core.camera.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSectionsTest {
    private val camera =
        Samples.proCamera().copy(
            lens = Samples.proCamera().lens.copy(zoomLabel = "1x"),
        )

    private fun rows(
        c: CameraInfo,
        section: String,
    ) = CameraSections
        .of(c)
        .first { it.title == section }
        .rows
        .associate { it.label to it.value }

    @Test
    fun `title carries id facing zoom and kind`() {
        assertEquals("Camera 0 · BACK · 1x · LOGICAL", CameraSections.title(camera))
        assertEquals("Camera 1 · FRONT · STANDALONE", CameraSections.title(Samples.basicCamera()))
    }

    @Test
    fun `summary line reflects manual and raw state`() {
        assertEquals("Manual · RAW · to 32 s", CameraSections.summaryLine(camera))
        assertEquals("Auto only · JPEG only · to 1/8 s", CameraSections.summaryLine(Samples.basicCamera()))
        assertEquals("Could not be read", CameraSections.summaryLine(Samples.basicCamera().copy(error = "x")))
    }

    @Test
    fun `exposure section formats ranges for photographers`() {
        val e = rows(camera, "Exposure")
        assertEquals("1/76923 s – 32 s", e["Shutter range"])
        assertEquals("50 – 6400", e["ISO range"])
        assertEquals("−2 EV to +2 EV", e["Exposure compensation"])
        assertEquals("32 s (0.031 fps)", e["Max frame duration"])
    }

    @Test
    fun `missing ranges say so instead of showing zeros`() {
        val e = rows(Samples.basicCamera().copy(exposure = ExposureInfo()), "Exposure")
        assertEquals("not reported", e["Shutter range"])
        assertEquals("not reported", e["ISO range"])
    }

    @Test
    fun `focus section converts diopters to distances`() {
        val f = rows(camera, "Focus")
        assertEquals("10 cm (10.0 dpt)", f["Nearest focus"])
        assertEquals("2.5 m (0.4 dpt)", f["Hyperfocal distance"])
        assertEquals("fixed focus", rows(Samples.basicCamera(), "Focus")["Nearest focus"])
    }

    @Test
    fun `output sizes are abbreviated after four entries`() {
        val many = (1..7).map { SizeInfo(4000 - it * 100, 3000 - it * 75) }
        val o = rows(camera.copy(outputs = OutputInfo(jpegSizes = many)), "Output sizes")
        assertTrue(o.getValue("JPEG").endsWith("+3 more"))
        assertEquals("none", o["YUV"])
    }

    @Test
    fun `sensor section derives megapixels`() {
        assertEquals("8160 × 6120 (49.9 MP)", rows(camera, "Sensor")["Pixel array"])
    }

    @Test
    fun `android 16 section reports cct range and hybrid ae`() {
        val a = rows(camera, "Android 16 additions")
        assertEquals("2000 – 10000 K", a["Kelvin range"])
        assertEquals("yes", a["Hybrid auto-exposure"])
    }

    @Test
    fun `request keys are listed as accepted or not`() {
        val k =
            rows(camera.copy(requestKeys = mapOf("android.lens.focusDistance" to false)), "Extensions and request keys")
        assertEquals("not accepted", k["lens.focusDistance"])
    }

    @Test
    fun `a camera that failed to read still produces an identity section with the error`() {
        val failed = Samples.basicCamera().copy(error = "Could not read this camera.")
        assertEquals("Could not read this camera.", rows(failed, "Identity")["Error"])
    }

    @Test
    fun `no section is ever empty`() {
        CameraSections.of(Samples.basicCamera()).forEach { assertFalse(it.title, it.rows.isEmpty()) }
    }

    @Test
    fun `noisy floats from the HAL are rounded for display`() {
        val noisy =
            camera.copy(
                lens = camera.lens.copy(controlZoomRatioMin = 1.0f, controlZoomRatioMax = 12.931958f),
                sensor = camera.sensor.copy(physicalWidthMm = 5.6448f, physicalHeightMm = 4.2336f),
            )
        assertEquals("1–12.9x", rows(noisy, "Lens")["Zoom ratio range"])
        assertEquals("5.64 × 4.23 mm", rows(noisy, "Sensor")["Physical size"])
    }
}
