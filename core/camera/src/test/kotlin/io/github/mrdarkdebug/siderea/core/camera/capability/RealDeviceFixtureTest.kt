package io.github.mrdarkdebug.siderea.core.camera.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the pure logic against a report captured from a real phone (a Pixel 10 on Android 17), so the
 * assumptions in the rules are checked against what hardware actually says, not what we imagine.
 */
class RealDeviceFixtureTest {
    private val report: CapabilityReport =
        CapabilityJson.decode(
            checkNotNull(javaClass.getResourceAsStream("/google-pixel-10-android-17.json")) { "missing fixture" }
                .bufferedReader()
                .use { it.readText() },
        )

    private fun camera(id: String) = report.cameras.first { it.id == id }

    private fun verdicts(id: String) = FeatureVerdicts.evaluate(camera(id), report.device.sdkInt).associateBy { it.id }

    @Test
    fun `the fixture is the pixel 10 report`() {
        assertEquals("Pixel 10", report.device.model)
        assertEquals(37, report.device.sdkInt)
        assertEquals(7, report.cameras.size)
    }

    @Test
    fun `summary picks the logical main back camera`() {
        val summary = CapabilitySummary.from(report)
        assertEquals("Google Pixel 10", summary.deviceName)
        assertEquals("0", summary.mainCamera?.id)
        assertEquals("16 s", summary.longestExposure)
        assertEquals("30–7518", summary.isoRange)
    }

    @Test
    fun `main lens can do 16 second RAW exposures with software priority modes and computed kelvin`() {
        val v = verdicts("0")
        assertEquals(SupportStatus.SUPPORTED, v.getValue(FeatureVerdicts.MANUAL_EXPOSURE).status)
        assertEquals(SupportStatus.SUPPORTED, v.getValue(FeatureVerdicts.LONG_EXPOSURE).status)
        assertEquals("16 s", v.getValue(FeatureVerdicts.LONG_EXPOSURE).detail)
        assertEquals(SupportStatus.SUPPORTED, v.getValue(FeatureVerdicts.RAW_CAPTURE).status)
        assertEquals("Up to 4000×3000.", v.getValue(FeatureVerdicts.RAW_CAPTURE).detail)
        assertEquals(SupportStatus.LIMITED, v.getValue(FeatureVerdicts.MANUAL_FOCUS).status)
        assertEquals(SupportStatus.LIMITED, v.getValue(FeatureVerdicts.KELVIN_WB).status)
        assertEquals(SupportStatus.LIMITED, v.getValue(FeatureVerdicts.HYBRID_AE).status)
    }

    @Test
    fun `the ultra-wide is fixed focus and the front lenses top out at one second`() {
        assertEquals(SupportStatus.UNSUPPORTED, verdicts("3").getValue(FeatureVerdicts.MANUAL_FOCUS).status)
        val front = verdicts("1").getValue(FeatureVerdicts.LONG_EXPOSURE)
        assertEquals(SupportStatus.LIMITED, front.status)
        assertTrue(front.detail.contains("Virtual Bulb"))
    }

    @Test
    fun `zoom labels recomputed from the real sensors match the stock camera`() {
        fun equivalent(id: String): Float {
            val c = camera(id)
            return ZoomLabels.equivalentFocalLengthMm(
                c.lens.focalLengthsMm.first(),
                c.sensor.physicalWidthMm,
                c.sensor.physicalHeightMm,
            )!!
        }
        val main = equivalent("2")
        assertEquals("0.6x", ZoomLabels.format(equivalent("3") / main))
        assertEquals("5x", ZoomLabels.format(equivalent("4") / main))
        assertEquals("2", ZoomLabels.pickMain(listOf("2" to main, "3" to equivalent("3"), "4" to equivalent("4"))))
    }

    @Test
    fun `physical cameras point at their logical parent`() {
        assertEquals(setOf("2", "3", "4"), camera("0").physicalIds.toSet())
        listOf("2", "3", "4").forEach { assertEquals("0", camera(it).parentLogicalId) }
        assertNotNull(camera("1").physicalIds.firstOrNull())
    }

    @Test
    fun `the phone offers neither Android 16 kelvin nor priority modes`() {
        val a16 = camera("0").android16
        assertTrue(a16.platformHasApi36)
        assertEquals(false, a16.cctSupported)
        assertEquals(false, a16.hybridAeSupported)
        // The keys Siderea will use instead are accepted.
        assertEquals(true, camera("0").requestKeys["android.colorCorrection.gains"])
        assertEquals(true, camera("0").requestKeys["android.colorCorrection.transform"])
    }
}
