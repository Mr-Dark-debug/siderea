package io.github.mrdarkdebug.siderea.ui.camera

import io.github.mrdarkdebug.siderea.core.camera.capability.CameraInfo
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityJson
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSettingsOpsTest {
    private val report =
        CapabilityJson.decode(
            checkNotNull(
                javaClass.getResourceAsStream("/google-pixel-10-android-17.json"),
            ).bufferedReader().use { it.readText() },
        )

    private fun limits(id: String) = ExposureLimits.from(report.cameras.first { it.id == id }, report.device.sdkInt)

    private fun camera(id: String): CameraInfo = report.cameras.first { it.id == id }

    @Test
    fun `shutter and iso combine into the four exposure modes`() {
        assertEquals(ExposureMode.AUTO, CameraSettingsOps.modeOf(false, false))
        assertEquals(ExposureMode.SHUTTER_PRIORITY, CameraSettingsOps.modeOf(true, false))
        assertEquals(ExposureMode.ISO_PRIORITY, CameraSettingsOps.modeOf(false, true))
        assertEquals(ExposureMode.MANUAL, CameraSettingsOps.modeOf(true, true))
    }

    @Test
    fun `toggling one control keeps the other`() {
        val auto = CaptureSettings()
        val s = CameraSettingsOps.withShutterManual(auto, true)
        assertEquals(ExposureMode.SHUTTER_PRIORITY, s.exposureMode)
        val manual = CameraSettingsOps.withIsoManual(s, true)
        assertEquals(ExposureMode.MANUAL, manual.exposureMode)
        assertEquals(ExposureMode.ISO_PRIORITY, CameraSettingsOps.withShutterManual(manual, false).exposureMode)
        assertEquals(
            ExposureMode.AUTO,
            CameraSettingsOps.withIsoManual(CameraSettingsOps.withShutterManual(manual, false), false).exposureMode,
        )
    }

    @Test
    fun `exposure compensation applies everywhere except full manual`() {
        assertTrue(CameraSettingsOps.evApplies(CaptureSettings(exposureMode = ExposureMode.AUTO)))
        assertTrue(CameraSettingsOps.evApplies(CaptureSettings(exposureMode = ExposureMode.SHUTTER_PRIORITY)))
        assertFalse(CameraSettingsOps.evApplies(CaptureSettings(exposureMode = ExposureMode.MANUAL)))
    }

    @Test
    fun `coercion clamps values to the lens`() {
        val s =
            CameraSettingsOps.coerce(
                CaptureSettings(shutterNs = Long.MAX_VALUE, iso = 1, evStops = 99f, focusDiopters = 99f),
                limits("0"),
            )
        assertEquals(limits("0").shutterMaxNs, s.shutterNs)
        assertEquals(30, s.iso)
        assertEquals(4f, s.evStops, 0.01f)
        assertEquals(10f, s.focusDiopters, 0f)
    }

    @Test
    fun `moving to the ultra-wide drops manual focus but keeps manual exposure`() {
        val manual = CaptureSettings(exposureMode = ExposureMode.MANUAL, focusMode = FocusMode.MANUAL)
        val s = CameraSettingsOps.coerce(manual, limits("3"))
        assertEquals(FocusMode.AUTO, s.focusMode)
        assertEquals(ExposureMode.MANUAL, s.exposureMode)
    }

    @Test
    fun `a lens without manual sensor goes back to automatic exposure`() {
        val basic =
            ExposureLimits.from(
                camera("0").copy(manualSensor = false, capabilities = listOf("BACKWARD_COMPATIBLE")),
                37,
            )
        val s = CameraSettingsOps.coerce(CaptureSettings(exposureMode = ExposureMode.MANUAL), basic)
        assertEquals(ExposureMode.AUTO, s.exposureMode)
    }

    @Test
    fun `a lens without raw falls back to jpeg`() {
        val noRaw = ExposureLimits.from(camera("0").copy(raw = false, capabilities = listOf("MANUAL_SENSOR")), 37)
        assertEquals(
            CaptureFormat.JPEG,
            CameraSettingsOps.coerce(CaptureSettings(format = CaptureFormat.RAW_JPEG), noRaw).format,
        )
    }

    @Test
    fun `format cycles and skips raw when unavailable`() {
        assertEquals(CaptureFormat.RAW_JPEG, CameraSettingsOps.nextFormat(CaptureFormat.JPEG, true))
        assertEquals(CaptureFormat.RAW, CameraSettingsOps.nextFormat(CaptureFormat.RAW_JPEG, true))
        assertEquals(CaptureFormat.JPEG, CameraSettingsOps.nextFormat(CaptureFormat.RAW, true))
        assertEquals(CaptureFormat.JPEG, CameraSettingsOps.nextFormat(CaptureFormat.JPEG, false))
        assertEquals("RAW+JPG", CameraSettingsOps.formatLabel(CaptureFormat.RAW_JPEG))
    }

    @Test
    fun `timer cycles through 2 5 and 10 seconds`() {
        assertEquals(
            listOf(2, 5, 10, 0),
            generateSequence(0) { CameraSettingsOps.nextTimer(it) }.drop(1).take(4).toList(),
        )
    }

    @Test
    fun `remembered state survives serialisation`() {
        val prefs =
            CameraPrefs(
                lensKey = "0:4",
                settings = CaptureSettings(exposureMode = ExposureMode.MANUAL, shutterNs = 8_000_000_000L, iso = 1600),
                aspect = "SIXTEEN_NINE",
                aids = Aids(grid = GridMode.THIRDS, peaking = true),
                timerSeconds = 5,
            )
        val json = Json { encodeDefaults = true }
        assertEquals(
            prefs,
            json.decodeFromString(CameraPrefs.serializer(), json.encodeToString(CameraPrefs.serializer(), prefs)),
        )
    }

    @Test
    fun `unknown future fields do not break reading old preferences`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(CameraPrefs.serializer(), """{"lensKey":"1","someNewThing":true}""")
        assertEquals("1", decoded.lensKey)
    }
}
