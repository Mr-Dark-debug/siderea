package io.github.mrdarkdebug.siderea.ui.camera

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SkyPreset
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition

/** The same applied exposure and schedule feed the UI, preflight and foreground service. */
object SkyCapturePolicy {
    fun settings(
        preset: SkyPreset,
        current: CaptureSettings,
        limits: ExposureLimits,
        focalMm: Float?,
    ): CaptureSettings {
        val pointStars = preset == SkyPreset.NIGHT_SKY || preset == SkyPreset.MILKY_WAY
        val starLimit =
            if (pointStars && focalMm != null &&
                focalMm > 0
            ) {
                (STAR_TRAILING_RULE / focalMm * NS_PER_SECOND).toLong()
            } else {
                Long.MAX_VALUE
            }
        val shutter = limits.clampShutter(minOf(preset.exposureNs, starLimit, limits.maxFrameDurationNs))
        return CameraSettingsOps.coerce(
            current.copy(
                exposureMode = ExposureMode.MANUAL,
                shutterNs = shutter,
                iso = limits.clampIso(preset.iso),
                focusMode = FocusMode.MANUAL,
                focusDiopters = 0f,
                evStops = 0f,
                format =
                    if (current.format ==
                        CaptureFormat.RAW
                    ) {
                        CaptureFormat.RAW_JPEG
                    } else {
                        current.format
                    },
            ),
            limits,
        )
    }

    fun usesSky(state: CameraUiState): Boolean =
        state.mode == CameraMode.ASTRO || (state.mode == CameraMode.TIMELAPSE && state.timelapse.astroTimelapse)

    fun kind(state: CameraUiState): SessionKind =
        when {
            state.mode == CameraMode.LONG_EXPOSURE -> SessionKind.LONG_EXPOSURE
            state.mode == CameraMode.ASTRO -> SessionKind.ASTRO
            usesSky(state) -> SessionKind.ASTRO_TIMELAPSE
            else -> SessionKind.TIMELAPSE
        }

    fun setup(state: CameraUiState): TimelapseSetup {
        val setup = state.timelapse
        if (state.mode == CameraMode.LONG_EXPOSURE) {
            return setup.copy(
                intervalMs = astroIntervalMs(state.effectiveShutterNs, 0, state.overhead),
                stop = if (setup.bulbSeconds == null) StopCondition.UNTIL_STOPPED else StopCondition.FRAME_COUNT,
                frameCount = setup.bulbSeconds?.let { bulbFrames(it, state.effectiveShutterNs) } ?: setup.frameCount,
                lockExposure = true,
                rampExposure = false,
                customInterval = true,
            )
        }
        if (!usesSky(state)) return setup
        val sky =
            setup.copy(
                intervalMs = astroIntervalMs(state.effectiveShutterNs, setup.astroGapMs, state.overhead),
                lockExposure = true,
                rampExposure = false,
                customInterval = true,
                astroTimelapse = true,
            )
        return if (state.mode == CameraMode.TIMELAPSE) {
            sky.copy(
                stop = if (setup.astroUntilStopped) StopCondition.UNTIL_STOPPED else StopCondition.DURATION,
                durationMs = setup.astroDurationMs,
                fps = setup.astroFps,
            )
        } else {
            sky
        }
    }

    private const val NS_PER_SECOND = 1_000_000_000L
    private const val STAR_TRAILING_RULE = 500.0
}
