package io.github.mrdarkdebug.siderea.core.camera.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitySummaryTest {
    private fun labelled(
        c: CameraInfo,
        label: String?,
    ) = c.copy(lens = c.lens.copy(zoomLabel = label))

    @Test
    fun `main camera is the back camera labelled 1x`() {
        val ultraWide = labelled(Samples.proCamera("2"), "0.6x")
        val main = labelled(Samples.proCamera("0"), "1x")
        val summary = CapabilitySummary.from(Samples.report(Samples.basicCamera("1"), ultraWide, main))
        assertEquals("0", summary.mainCamera?.id)
        assertEquals(3, summary.cameraCount)
        assertTrue(summary.manualSensor)
        assertTrue(summary.raw)
        assertEquals("32 s", summary.longestExposure)
        assertEquals("50–6400", summary.isoRange)
    }

    @Test
    fun `logical 1x is preferred over its physical twin`() {
        val physical = labelled(Samples.proCamera("2"), "1x").copy(kind = CameraKind.PHYSICAL)
        val logical = labelled(Samples.proCamera("0"), "1x")
        assertEquals("0", CapabilitySummary.from(Samples.report(physical, logical)).mainCamera?.id)
    }

    @Test
    fun `without a 1x label the first back camera wins`() {
        val summary = CapabilitySummary.from(Samples.report(Samples.basicCamera("1"), Samples.proCamera("0")))
        assertEquals("0", summary.mainCamera?.id)
    }

    @Test
    fun `cameras that errored are never the main camera`() {
        val broken = Samples.proCamera("0").copy(error = "boom")
        val summary = CapabilitySummary.from(Samples.report(broken, Samples.basicCamera("1")))
        assertEquals("1", summary.mainCamera?.id)
    }

    @Test
    fun `a phone with no readable camera still yields a summary`() {
        val summary = CapabilitySummary.from(Samples.report())
        assertNull(summary.mainCamera)
        assertFalse(summary.manualSensor)
        assertNull(summary.longestExposure)
        assertEquals(0, summary.cameraCount)
    }

    @Test
    fun `device name does not repeat the manufacturer`() {
        fun name(
            maker: String,
            model: String,
        ) = CapabilitySummary.deviceName(
            Samples.report().device.copy(manufacturer = maker, model = model),
        )
        assertEquals("Google Pixel 9 Pro", name("Google", "Pixel 9 Pro"))
        assertEquals("Samsung SM-S928B", name("samsung", "SM-S928B"))
        assertEquals("OnePlus 12", name("OnePlus", "OnePlus 12"))
        assertEquals("This phone", name("", ""))
    }

    @Test
    fun `android label includes the API level`() {
        assertEquals("Android 16 (API 36)", CapabilitySummary.from(Samples.report()).androidLabel)
    }
}
