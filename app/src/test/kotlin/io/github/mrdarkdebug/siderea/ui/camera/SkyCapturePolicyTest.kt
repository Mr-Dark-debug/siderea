package io.github.mrdarkdebug.siderea.ui.camera

import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityJson
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SkyPreset
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.timelapse.IntervalMath
import io.github.mrdarkdebug.siderea.core.capture.timelapse.OverheadEstimate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyCapturePolicyTest {
    private val report =
        CapabilityJson.decode(
            checkNotNull(
                javaClass.getResourceAsStream("/google-pixel-10-android-17.json"),
            ).bufferedReader().use {
                it.readText()
            },
        )
    private val limits = ExposureLimits.from(report.cameras.first { it.id == "0" }, report.device.sdkInt)

    @Test fun `presets respect ISO exposure and frame duration limits`() {
        val limited = limits.copy(shutterMaxNs = 2_000_000_000L, maxFrameDurationNs = 1_000_000_000L, isoMax = 800)
        val settings =
            SkyCapturePolicy.settings(
                SkyPreset.MILKY_WAY,
                CaptureSettings(format = CaptureFormat.RAW),
                limited,
                24f,
            )
        assertEquals(1_000_000_000L, settings.shutterNs)
        assertEquals(800, settings.iso)
        assertEquals(FocusMode.MANUAL, settings.focusMode)
        assertEquals(0f, settings.focusDiopters, 0f)
        assertEquals(CaptureFormat.RAW_JPEG, settings.format)
    }

    @Test fun `point stars use approximate trailing limit but trails do not`() {
        val wideLimits = limits.copy(shutterMaxNs = 60_000_000_000L, maxFrameDurationNs = 60_000_000_000L)
        assertEquals(
            5_000_000_000L,
            SkyCapturePolicy.settings(SkyPreset.MILKY_WAY, CaptureSettings(), wideLimits, 100f).shutterNs,
        )
        assertEquals(
            20_000_000_000L,
            SkyCapturePolicy.settings(SkyPreset.STAR_TRAILS, CaptureSettings(), wideLimits, 100f).shutterNs,
        )
    }

    @Test fun `unsupported lenses do not acquire fake manual controls`() {
        val settings =
            SkyCapturePolicy.settings(
                SkyPreset.NIGHT_SKY,
                CaptureSettings(),
                limits.copy(manualFocus = false, manualExposure = false),
                null,
            )
        assertEquals(ExposureMode.AUTO, settings.exposureMode)
        assertEquals(FocusMode.AUTO, settings.focusMode)
    }

    @Test fun `astro timelapse cadence exceeds capture overhead and preserves normal interval`() {
        val state =
            CameraUiState(
                mode = CameraMode.TIMELAPSE,
                effectiveShutterNs = 15_000_000_000L,
                overhead = OverheadEstimate(4000, true, 3),
                timelapse =
                    TimelapseSetup(
                        astroTimelapse = true,
                        intervalMs = 1000,
                        rampExposure = true,
                        astroDurationMs = 300_000,
                        skyPreset = SkyPreset.MILKY_WAY,
                    ),
            )
        val setup = SkyCapturePolicy.setup(state)
        assertEquals(19_250L, setup.intervalMs)
        assertTrue(IntervalMath.check(setup.intervalMs, state.effectiveShutterNs, state.overhead).ok)
        assertEquals(StopCondition.DURATION, setup.stop)
        assertEquals(300_000L, setup.durationMs)
        assertEquals(24, setup.fps)
        assertFalse(setup.rampExposure)
        assertTrue(setup.lockExposure)
        assertEquals(SessionKind.ASTRO_TIMELAPSE, SkyCapturePolicy.kind(state))
        assertEquals(
            1000L,
            SkyCapturePolicy.setup(state.copy(timelapse = state.timelapse.copy(astroTimelapse = false))).intervalMs,
        )
    }

    @Test fun `old saved preferences gain compatible sky defaults`() {
        val preferences =
            Json {
                ignoreUnknownKeys = true
            }.decodeFromString<CameraPrefs>("""{"timelapse":{"intervalMs":5000}}""")
        assertFalse(preferences.timelapse.astroTimelapse)
        assertEquals(SkyPreset.NIGHT_SKY, preferences.timelapse.skyPreset)
        assertEquals(5, preferences.timelapse.skyDelaySeconds)
    }
}
