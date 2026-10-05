package io.github.mrdarkdebug.siderea.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mrdarkdebug.siderea.capture.CaptureSessionState
import io.github.mrdarkdebug.siderea.capture.RunState
import io.github.mrdarkdebug.siderea.core.capture.session.FrameRecord
import io.github.mrdarkdebug.siderea.core.capture.session.SessionManifest
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStore
import io.github.mrdarkdebug.siderea.core.capture.session.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Everything the detail screen shows about one session. */
data class SessionDetail(
    val manifest: SessionManifest,
    val summary: SessionSummary,
    val usableFrames: Int,
    val failedFrames: Int,
    val flaggedFrames: Int,
    /** Preview images spread across the session: first, middle, last. */
    val previews: List<File>,
    val folder: File,
    val canResume: Boolean,
)

private const val SPREAD_COUNT = 3

@HiltViewModel
class SessionsViewModel
    @Inject
    constructor(
        private val store: SessionStore,
        private val run: CaptureSessionState,
    ) : ViewModel() {
        private val mutableSessions = MutableStateFlow<List<SessionSummary>>(emptyList())
        val sessions: StateFlow<List<SessionSummary>> = mutableSessions.asStateFlow()

        private val mutableDetail = MutableStateFlow<SessionDetail?>(null)
        val detail: StateFlow<SessionDetail?> = mutableDetail.asStateFlow()

        fun refresh() {
            viewModelScope.launch(Dispatchers.IO) { mutableSessions.value = store.list() }
        }

        fun load(id: String) {
            viewModelScope.launch(Dispatchers.IO) { mutableDetail.value = buildDetail(id) }
        }

        fun rename(
            id: String,
            name: String,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                store.rename(id, name)
                mutableDetail.value = buildDetail(id)
                mutableSessions.value = store.list()
            }
        }

        fun delete(
            id: String,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { store.delete(id) }
                mutableSessions.value = withContext(Dispatchers.IO) { store.list() }
                mutableDetail.value = null
                onDone()
            }
        }

        /** The first preview image of a session, for list thumbnails. */
        suspend fun thumbnail(summary: SessionSummary): File? =
            withContext(Dispatchers.IO) {
                File(store.directory(summary.id), "previews")
                    .listFiles { f -> f.extension == "jpg" }
                    ?.minByOrNull { it.name }
            }

        private fun buildDetail(id: String): SessionDetail? {
            val handle = store.open(id) ?: return null
            val frames: List<FrameRecord> = handle.frames
            val previews =
                File(handle.dir, "previews")
                    .listFiles { f ->
                        f.extension == "jpg"
                    }.orEmpty()
                    .sortedBy { it.name }
            val spread =
                if (previews.size <= SPREAD_COUNT) {
                    previews
                } else {
                    listOf(previews.first(), previews[previews.size / 2], previews.last())
                }
            val running = (run.state.value as? RunState.Running)?.sessionId == id
            return SessionDetail(
                manifest = handle.manifest,
                summary = handle.summary(),
                usableFrames = frames.count { it.error == null },
                failedFrames = frames.count { it.error != null },
                flaggedFrames = frames.count { it.flags.isNotEmpty() },
                previews = spread,
                folder = handle.dir,
                canResume =
                    !running &&
                        handle.manifest.status ==
                        io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus.RUNNING,
            )
        }
    }
