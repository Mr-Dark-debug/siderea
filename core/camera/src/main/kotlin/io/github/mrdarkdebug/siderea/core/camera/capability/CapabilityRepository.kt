package io.github.mrdarkdebug.siderea.core.camera.capability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface CapabilityState {
    data object Loading : CapabilityState

    data class Ready(
        val report: CapabilityReport,
    ) : CapabilityState

    data class Failed(
        val message: String,
    ) : CapabilityState
}

/**
 * Holds the one capability report the whole app shares. It is read once and refreshed on demand;
 * home, the inspector and (later) the capture engine all observe the same state.
 */
class CapabilityRepository(
    private val source: CapabilitySource,
) {
    private val mutableState = MutableStateFlow<CapabilityState>(CapabilityState.Loading)
    val state: StateFlow<CapabilityState> = mutableState.asStateFlow()

    private val lock = Mutex()

    /** Reads the report if nothing has been read yet. */
    suspend fun ensureLoaded() {
        if (mutableState.value is CapabilityState.Ready) return
        refresh()
    }

    /** Always re-reads, e.g. after the user closed another camera app. */
    suspend fun refresh() =
        lock.withLock {
            mutableState.value = CapabilityState.Loading
            mutableState.value =
                try {
                    CapabilityState.Ready(source.read())
                } catch (e: SecurityException) {
                    CapabilityState.Failed(
                        "Android wouldn't let Siderea read the cameras (${e.message}). " +
                            "Check that the camera isn't disabled for this device.",
                    )
                } catch (e: IllegalStateException) {
                    CapabilityState.Failed(
                        "Reading the cameras failed (${e.message}). Close other camera apps and retry.",
                    )
                }
        }
}
