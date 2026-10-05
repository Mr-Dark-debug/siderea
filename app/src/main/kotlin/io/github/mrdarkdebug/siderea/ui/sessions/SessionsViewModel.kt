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
import io.github.mrdarkdebug.siderea.core.export.ExportMath
import io.github.mrdarkdebug.siderea.core.export.FrameDecoder
import io.github.mrdarkdebug.siderea.core.export.PixelSize
import io.github.mrdarkdebug.siderea.core.export.VideoCodec
import io.github.mrdarkdebug.siderea.core.export.VideoEncoders
import io.github.mrdarkdebug.siderea.core.export.VideoSpec
import io.github.mrdarkdebug.siderea.export.ExportCoordinator
import io.github.mrdarkdebug.siderea.export.ExportState
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
    /** Frames that have a JPEG, which is what video export uses. */
    val jpegFrames: Int,
    val movedFrames: Int,
    val firstJpeg: File?,
    val hasRaw: Boolean,
)

/** What a video export would produce, shown before the person commits to it. */
data class VideoEstimate(
    val frames: Int,
    val size: PixelSize,
    val seconds: Double,
    val bytes: Long,
)

private const val SPREAD_COUNT = 3
private const val MOVED = "moved"

@HiltViewModel
class SessionsViewModel
    @Inject
    constructor(
        private val store: SessionStore,
        private val run: CaptureSessionState,
        private val exports: ExportCoordinator,
    ) : ViewModel() {
        val exportState: StateFlow<ExportState> = exports.state

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

        fun exportVideo(
            id: String,
            spec: VideoSpec,
            skipMoved: Boolean,
        ) = exports.startVideo(id, spec, skipMoved)

        fun exportZip(
            id: String,
            includeRaw: Boolean,
        ) = exports.startZip(id, includeRaw)

        fun exportTiff(
            id: String,
            frameName: String,
        ) = exports.startTiff(id, frameName)

        fun cancelExport() = exports.cancel()

        fun dismissExport() = exports.dismiss()

        fun saveToGallery(done: ExportState.Done) = exports.saveToGallery(done)

        fun shareUri(done: ExportState.Done) = exports.shareUri(done)

        /** The video codecs this phone can encode. */
        fun codecs(): List<VideoCodec> = VideoEncoders.available()

        suspend fun estimate(
            detail: SessionDetail,
            spec: VideoSpec,
            skipMoved: Boolean,
        ): VideoEstimate? =
            withContext(Dispatchers.IO) {
                val first = detail.firstJpeg ?: return@withContext null
                val source = runCatching { FrameDecoder.size(first) }.getOrNull() ?: return@withContext null
                val crop = ExportMath.cropRect(source.width, source.height, spec.crop, spec.panX, spec.panY)
                val size = ExportMath.outputSize(crop.width, crop.height, spec.size)
                val frames = detail.jpegFrames - if (skipMoved) detail.movedFrames else 0
                VideoEstimate(
                    frames,
                    size,
                    ExportMath.videoSeconds(frames, spec.fps),
                    ExportMath.estimatedBytes(frames, size, spec),
                )
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
            val jpegs = frames.filter { it.hasJpeg && it.error == null }
            return SessionDetail(
                manifest = handle.manifest,
                jpegFrames = jpegs.size,
                movedFrames = jpegs.count { MOVED in it.flags },
                firstJpeg = jpegs.minByOrNull { it.index }?.let { handle.jpegFile(it.name) }?.takeIf { it.exists() },
                hasRaw = frames.any { it.hasDng },
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
