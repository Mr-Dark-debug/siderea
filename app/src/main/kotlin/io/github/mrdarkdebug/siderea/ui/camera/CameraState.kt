package io.github.mrdarkdebug.siderea.ui.camera

import io.github.mrdarkdebug.siderea.capture.RunState
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.engine.AspectRatio
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineState
import io.github.mrdarkdebug.siderea.core.camera.engine.FrameInfo
import io.github.mrdarkdebug.siderea.core.camera.engine.Lens
import io.github.mrdarkdebug.siderea.core.camera.engine.ReadyInfo
import io.github.mrdarkdebug.siderea.core.capture.session.SessionSummary
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.timelapse.OverheadEstimate
import io.github.mrdarkdebug.siderea.core.capture.timelapse.PreflightItem
import io.github.mrdarkdebug.siderea.device.DeviceStatus
import kotlinx.serialization.Serializable

enum class PermissionState { UNKNOWN, GRANTED, DENIED }

/** The shooting modes on the strip. A mode that isn't built yet says which release brings it. */
enum class CameraMode(
    val label: String,
    val availableSince: String?,
) {
    PHOTO("PHOTO", null),
    TIMELAPSE("TIMELAPSE", null),
    ASTRO("ASTRO", null),
    LONG_EXPOSURE("LONG EXPOSURE", null),
    ;

    /** Modes that run a scheduled session in the foreground service instead of taking one photo. */
    val runsSessions: Boolean get() = this == TIMELAPSE || this == ASTRO || this == LONG_EXPOSURE
}

enum class GridMode { OFF, THIRDS, CENTER }

/** The controls that open a panel above the shutter. */
enum class ControlPanel { SHUTTER, ISO, EV, FOCUS, WB, AIDS, TIMELAPSE, ASTRO, BULB }

@Serializable
data class Aids(
    val grid: GridMode = GridMode.OFF,
    val level: Boolean = true,
    val peaking: Boolean = false,
    val zebra: Boolean = false,
    val histogram: Boolean = true,
    /** Brighten the viewfinder only, so you can compose in the dark. The saved photo is unchanged. */
    val nightView: Boolean = false,
)

/** What the user has chosen for a timelapse. Remembered between launches. */
@Serializable
data class TimelapseSetup(
    val intervalMs: Long = 5_000,
    val stop: StopCondition = StopCondition.FRAME_COUNT,
    val frameCount: Int = 300,
    val durationMs: Long = 600_000,
    val lockExposure: Boolean = true,
    val fps: Int = 30,
    val adaptToHeat: Boolean = true,
    /** Keep the screen on (dimmed to black) so Android never puts the phone into deep sleep. */
    val keepScreenOn: Boolean = true,
    /** True once the interval was typed in with the ruler rather than picked from the presets. */
    val customInterval: Boolean = false,
    /** Astro: pause between one frame ending and the next starting. The interval is exposure plus this. */
    val astroGapMs: Long = 2_000,
    /** Follow changing light with a smooth exposure ramp (needs JPEG frames to measure). */
    val rampExposure: Boolean = false,
    /** Virtual bulb: total exposure in seconds, or null to keep going until stopped. */
    val bulbSeconds: Int? = 120,
)

sealed interface CaptureUi {
    data object Idle : CaptureUi

    data class Countdown(
        val secondsLeft: Int,
    ) : CaptureUi

    data class Exposing(
        val durationNs: Long,
        val startedAtElapsedMs: Long?,
    ) : CaptureUi

    data object Saving : CaptureUi
}

class UiMessage(
    val id: Long,
    val text: String,
)

/** What the camera screen shows. */
data class CameraUiState(
    val permission: PermissionState = PermissionState.UNKNOWN,
    val capabilitiesLoaded: Boolean = false,
    val capabilityError: String? = null,
    val lenses: List<Lens> = emptyList(),
    val lens: Lens? = null,
    val limits: ExposureLimits? = null,
    val engine: EngineState = EngineState.Closed,
    val ready: ReadyInfo? = null,
    val settings: CaptureSettings = CaptureSettings(),
    val aspect: AspectRatio = AspectRatio.FOUR_THREE,
    val aids: Aids = Aids(),
    val mode: CameraMode = CameraMode.PHOTO,
    val timerSeconds: Int = 0,
    val panel: ControlPanel? = null,
    val live: FrameInfo? = null,
    /** Shutter and ISO in effect, which in S and I priority include the software-metered value. */
    val effectiveShutterNs: Long = CaptureSettings.DEFAULT_SHUTTER_NS,
    val effectiveIso: Int = CaptureSettings.DEFAULT_ISO,
    val aeLimited: Boolean = false,
    val displayGain: Float = 1f,
    val capture: CaptureUi = CaptureUi.Idle,
    val lastPhotoUri: String? = null,
    val device: DeviceStatus = DeviceStatus.UNKNOWN,
    val message: UiMessage? = null,
    val timelapse: TimelapseSetup = TimelapseSetup(),
    val run: RunState = RunState.Idle,
    val interrupted: List<SessionSummary> = emptyList(),
    val overhead: OverheadEstimate = OverheadEstimate(0, measured = false, samples = 0),
    val preflight: List<PreflightItem>? = null,
    val measuring: Boolean = false,
) {
    val isCapturing: Boolean get() = capture != CaptureUi.Idle
}

/** What is remembered between launches. Plain fields so the format survives refactoring. */
@Serializable
data class CameraPrefs(
    val lensKey: String? = null,
    val settings: CaptureSettings = CaptureSettings(),
    val aspect: String = AspectRatio.FOUR_THREE.name,
    val aids: Aids = Aids(),
    val timerSeconds: Int = 0,
    val timelapse: TimelapseSetup = TimelapseSetup(),
)
