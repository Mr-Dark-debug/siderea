package io.github.mrdarkdebug.siderea.ui.camera

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.engine.AspectRatio
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineState
import io.github.mrdarkdebug.siderea.core.camera.engine.FrameInfo
import io.github.mrdarkdebug.siderea.core.camera.engine.Lens
import io.github.mrdarkdebug.siderea.core.camera.engine.ReadyInfo
import io.github.mrdarkdebug.siderea.device.DeviceStatus
import kotlinx.serialization.Serializable

enum class PermissionState { UNKNOWN, GRANTED, DENIED }

/** The shooting modes on the strip. A mode that isn't built yet says which release brings it. */
enum class CameraMode(
    val label: String,
    val availableSince: String?,
) {
    PHOTO("PHOTO", null),
    TIMELAPSE("TIMELAPSE", "v0.3.0"),
    ASTRO("ASTRO", "v0.5.0"),
    LONG_EXPOSURE("LONG EXPOSURE", "v0.6.0"),
}

enum class GridMode { OFF, THIRDS, CENTER }

/** The controls that open a panel above the shutter. */
enum class ControlPanel { SHUTTER, ISO, EV, FOCUS, WB, AIDS }

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
)
