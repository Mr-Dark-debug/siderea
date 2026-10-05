package io.github.mrdarkdebug.siderea.core.camera.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureVerdictsTest {
    private fun verdicts(
        camera: CameraInfo,
        sdk: Int = 36,
    ) = FeatureVerdicts.evaluate(camera, sdk).associateBy { it.id }

    @Test
    fun `every evaluation covers every feature exactly once`() {
        val result = FeatureVerdicts.evaluate(Samples.proCamera(), 36)
        assertEquals(result.map { it.id }.toSet().size, result.size)
        assertEquals(8, result.size)
    }

    @Test
    fun `a fully capable lens supports every Siderea control`() {
        val v = verdicts(Samples.proCamera())
        listOf(
            FeatureVerdicts.MANUAL_EXPOSURE,
            FeatureVerdicts.LONG_EXPOSURE,
            FeatureVerdicts.RAW_CAPTURE,
            FeatureVerdicts.MANUAL_FOCUS,
            FeatureVerdicts.KELVIN_WB,
            FeatureVerdicts.HYBRID_AE,
        ).forEach { assertEquals(it, SupportStatus.SUPPORTED, v.getValue(it).status) }
    }

    @Test
    fun `short maximum exposure points the user at virtual bulb`() {
        val camera =
            Samples.proCamera().copy(
                exposure = Samples.proCamera().exposure.copy(exposureTimeMaxNs = 500_000_000L),
            )
        val verdict = verdicts(camera).getValue(FeatureVerdicts.LONG_EXPOSURE)
        assertEquals(SupportStatus.LIMITED, verdict.status)
        assertEquals("This lens can't expose longer than 1/2 s. Use Virtual Bulb for longer exposures.", verdict.detail)
    }

    @Test
    fun `ten seconds is the astro-grade threshold`() {
        fun status(maxNs: Long) =
            verdicts(
                Samples.proCamera().copy(exposure = Samples.proCamera().exposure.copy(exposureTimeMaxNs = maxNs)),
            ).getValue(FeatureVerdicts.LONG_EXPOSURE).status
        assertEquals(SupportStatus.SUPPORTED, status(10 * Samples.SECOND_NS))
        assertEquals(SupportStatus.LIMITED, status(10 * Samples.SECOND_NS - 1))
    }

    @Test
    fun `a lens without MANUAL_SENSOR has no manual or long exposure`() {
        val v = verdicts(Samples.basicCamera())
        assertEquals(SupportStatus.UNSUPPORTED, v.getValue(FeatureVerdicts.MANUAL_EXPOSURE).status)
        assertEquals(SupportStatus.UNSUPPORTED, v.getValue(FeatureVerdicts.LONG_EXPOSURE).status)
        assertTrue(v.getValue(FeatureVerdicts.MANUAL_EXPOSURE).detail.contains("exposure compensation"))
    }

    @Test
    fun `a manual-sensor camera whose HAL rejects the exposure key is not manual`() {
        val camera = Samples.proCamera().copy(requestKeys = mapOf("android.sensor.exposureTime" to false))
        assertEquals(SupportStatus.UNSUPPORTED, verdicts(camera).getValue(FeatureVerdicts.MANUAL_EXPOSURE).status)
    }

    @Test
    fun `RAW needs both the capability and a RAW size`() {
        val noSizes = Samples.proCamera().copy(outputs = OutputInfo())
        assertEquals(SupportStatus.LIMITED, verdicts(noSizes).getValue(FeatureVerdicts.RAW_CAPTURE).status)
        assertEquals(
            SupportStatus.UNSUPPORTED,
            verdicts(Samples.basicCamera()).getValue(FeatureVerdicts.RAW_CAPTURE).status,
        )
    }

    @Test
    fun `fixed focus lenses have no manual focus`() {
        val fixed = Samples.proCamera().copy(focus = FocusInfo(minimumFocusDistanceDiopters = 0f))
        assertEquals(SupportStatus.UNSUPPORTED, verdicts(fixed).getValue(FeatureVerdicts.MANUAL_FOCUS).status)
    }

    @Test
    fun `uncalibrated focus is limited with advice`() {
        val camera = Samples.proCamera().copy(focus = Samples.proCamera().focus.copy(calibration = "UNCALIBRATED"))
        val verdict = verdicts(camera).getValue(FeatureVerdicts.MANUAL_FOCUS)
        assertEquals(SupportStatus.LIMITED, verdict.status)
        assertTrue(verdict.detail.contains("zoomed preview"))
    }

    @Test
    fun `kelvin white balance needs Android 16 and says so on older systems`() {
        val camera = Samples.proCamera().copy(android16 = Android16Info(platformHasApi36 = false))
        val verdict = verdicts(camera, sdk = 35).getValue(FeatureVerdicts.KELVIN_WB)
        assertEquals(SupportStatus.UNSUPPORTED, verdict.status)
        assertTrue(verdict.detail.contains("Android 16"))
    }

    @Test
    fun `kelvin falls back to gains on Android 16 phones without CCT`() {
        val camera =
            Samples.proCamera().copy(
                android16 = Android16Info(platformHasApi36 = true),
                requestKeys = mapOf("android.colorCorrection.gains" to true),
            )
        assertEquals(SupportStatus.LIMITED, verdicts(camera, 36).getValue(FeatureVerdicts.KELVIN_WB).status)
    }

    @Test
    fun `kelvin without tint is limited`() {
        val camera =
            Samples.proCamera().copy(
                android16 = Samples.proCamera().android16.copy(colorTintKeyAvailable = false),
            )
        assertEquals(SupportStatus.LIMITED, verdicts(camera).getValue(FeatureVerdicts.KELVIN_WB).status)
    }

    @Test
    fun `hybrid AE is provided in software when the camera has no priority modes`() {
        val none = Samples.proCamera().copy(android16 = Android16Info(platformHasApi36 = true))
        val onNewPhone = verdicts(none).getValue(FeatureVerdicts.HYBRID_AE)
        assertEquals(SupportStatus.LIMITED, onNewPhone.status)
        assertTrue(onNewPhone.detail.contains("software"))
        assertTrue(verdicts(none, sdk = 35).getValue(FeatureVerdicts.HYBRID_AE).detail.contains("Android 16"))
    }

    @Test
    fun `a camera that moves its lens but hides its nearest focus distance still gets manual focus`() {
        // Real behaviour of a Pixel 10: afModes include AUTO, minimumFocusDistance is null.
        val camera = Samples.proCamera().copy(focus = FocusInfo(minimumFocusDistanceDiopters = null))
        val verdict = verdicts(camera).getValue(FeatureVerdicts.MANUAL_FOCUS)
        assertEquals(SupportStatus.LIMITED, verdict.status)
        assertTrue(verdict.detail.contains("assumes 10 cm"))
    }

    @Test
    fun `a camera with only the OFF autofocus mode and no focus distance is fixed focus`() {
        val camera =
            Samples.proCamera().copy(
                focus = FocusInfo(minimumFocusDistanceDiopters = null),
                modes = Samples.proCamera().modes.copy(afModes = listOf("OFF")),
            )
        assertEquals(SupportStatus.UNSUPPORTED, verdicts(camera).getValue(FeatureVerdicts.MANUAL_FOCUS).status)
    }

    @Test
    fun `night extension is informational and detected by name`() {
        assertEquals(
            SupportStatus.SUPPORTED,
            verdicts(Samples.proCamera()).getValue(FeatureVerdicts.NIGHT_EXTENSION).status,
        )
        assertEquals(
            SupportStatus.UNSUPPORTED,
            verdicts(Samples.basicCamera()).getValue(FeatureVerdicts.NIGHT_EXTENSION).status,
        )
    }

    @Test
    fun `every non-supported verdict explains itself`() {
        FeatureVerdicts
            .evaluate(Samples.basicCamera(), 29)
            .filter { it.status != SupportStatus.SUPPORTED }
            .forEach { assertTrue("${it.id} needs a human explanation", it.detail.length > 20) }
    }
}
