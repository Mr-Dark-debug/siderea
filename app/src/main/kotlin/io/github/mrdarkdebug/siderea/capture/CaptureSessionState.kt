package io.github.mrdarkdebug.siderea.capture

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the service needs to run one session. Sent as JSON in the start intent. */
@Serializable
data class LaunchRequest(
    val lensKey: String,
    val settings: CaptureSettings,
    val aspect: String,
    val config: TimelapseConfig,
    /** Shutter and ISO used for every frame when exposure is locked (the values metered at the start). */
    val lockedShutterNs: Long,
    val lockedIso: Int,
    val jpegOrientation: Int,
    val keepScreenOn: Boolean,
    val resumeSessionId: String? = null,
    val name: String? = null,
)

/** What a running or just-finished session looks like to the UI. */
sealed interface RunState {
    data object Idle : RunState

    data class Running(
        val sessionId: String,
        val name: String,
        val kind: SessionKind,
        val format: CaptureFormat,
        val frames: Int,
        val plannedFrames: Int?,
        val startedAtElapsedMs: Long,
        val intervalMs: Long,
        val lastPreviewPath: String?,
        val lastFrameAtElapsedMs: Long?,
        val notices: List<String>,
        val overheadMs: Long?,
        val overheadMeasured: Boolean,
        val keepScreenOn: Boolean,
        val stopping: Boolean = false,
    ) : RunState

    data class Finished(
        val sessionId: String,
        val status: SessionStatus,
        val frames: Int,
        val message: String?,
    ) : RunState
}

/**
 * The one place the UI and the capture service meet. The service writes, screens read. It lives in a
 * singleton because the service can outlive every screen.
 */
@Singleton
class CaptureSessionState
    @Inject
    constructor() {
        private val mutable = MutableStateFlow<RunState>(RunState.Idle)
        val state: StateFlow<RunState> = mutable.asStateFlow()

        val isRunning: Boolean get() = mutable.value is RunState.Running

        fun set(state: RunState) {
            mutable.value = state
        }

        fun update(change: (RunState.Running) -> RunState.Running) {
            val current = mutable.value
            if (current is RunState.Running) mutable.value = change(current)
        }

        fun dismissFinished() {
            if (mutable.value is RunState.Finished) mutable.value = RunState.Idle
        }
    }
