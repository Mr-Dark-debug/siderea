package io.github.mrdarkdebug.siderea.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mrdarkdebug.siderea.core.capture.session.SessionHandle
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStore
import io.github.mrdarkdebug.siderea.core.export.BitmapTiff
import io.github.mrdarkdebug.siderea.core.export.ExportException
import io.github.mrdarkdebug.siderea.core.export.ExportPhase
import io.github.mrdarkdebug.siderea.core.export.MediaStorePublisher
import io.github.mrdarkdebug.siderea.core.export.PublishKind
import io.github.mrdarkdebug.siderea.core.export.TimelapseVideoExporter
import io.github.mrdarkdebug.siderea.core.export.VideoSpec
import io.github.mrdarkdebug.siderea.core.export.ZipExporter
import io.github.mrdarkdebug.siderea.core.export.ZipSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ExportState {
    data object Idle : ExportState

    data class Working(
        val sessionId: String,
        val title: String,
        val phase: String,
        val done: Int,
        val total: Int,
    ) : ExportState

    data class Done(
        val sessionId: String,
        val title: String,
        val file: File,
        val mime: String,
        val kind: PublishKind,
        val detail: String,
    ) : ExportState

    data class Failed(
        val sessionId: String,
        val message: String,
    ) : ExportState
}

/**
 * Runs exports in the app's own scope, so leaving the screen does not cancel one, and starts [ExportService] so
 * Android keeps the process alive while it works. Results land in the session's `exports/` folder; saving to the
 * gallery or sharing is a separate, explicit step.
 */
@Singleton
class ExportCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: SessionStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val mutable = MutableStateFlow<ExportState>(ExportState.Idle)
        val state: StateFlow<ExportState> = mutable.asStateFlow()
        private var job: Job? = null

        val busy: Boolean get() = mutable.value is ExportState.Working

        /** Makes a video from the session's JPEG frames. Frames the gyro flagged as moved can be left out. */
        fun startVideo(
            sessionId: String,
            spec: VideoSpec,
            skipMoved: Boolean,
        ) {
            val handle = begin(sessionId) ?: return
            val title = "Video"
            val files = jpegFrames(handle, skipMoved)
            val name = "${handle.dir.name}_${spec.size.label}_${spec.fps}fps.mp4"
            enqueue(sessionId, title) {
                val result =
                    TimelapseVideoExporter().export(files, spec, File(handle.exportsDir(), name)) { p ->
                        mutable.value = ExportState.Working(sessionId, title, phaseLabel(p.phase), p.done, p.total)
                    }
                val shrink =
                    if (result.shrunk) " Made smaller because this phone's encoder couldn't do the full size." else ""
                val skip = if (result.skipped > 0) " ${result.skipped} unreadable frames were left out." else ""
                ExportState.Done(
                    sessionId,
                    title,
                    result.file,
                    "video/mp4",
                    PublishKind.VIDEO,
                    "${result.frames} frames, ${result.size.width}x${result.size.height}, " +
                        "${"%.1f".format(result.seconds)} s.$shrink$skip",
                )
            }
        }

        /** Zips the session's frames (and `session.json`) for moving to a computer. */
        fun startZip(
            sessionId: String,
            includeRaw: Boolean,
        ) {
            val handle = begin(sessionId) ?: return
            val title = "ZIP"
            val sources = zipSources(handle, includeRaw)
            val output = File(handle.exportsDir(), "${handle.dir.name}.zip")
            enqueue(sessionId, title) {
                ZipExporter.zip(sources, output) { done, total ->
                    mutable.value = ExportState.Working(sessionId, title, "Packing files", done, total)
                }
                ExportState.Done(
                    sessionId,
                    title,
                    output,
                    "application/zip",
                    PublishKind.DOWNLOAD,
                    "${sources.size} files, ${output.length() / BYTES_PER_MB} MB.",
                )
            }
        }

        /** Saves one frame as an 8-bit TIFF. */
        fun startTiff(
            sessionId: String,
            frameName: String,
        ) {
            val handle = begin(sessionId) ?: return
            val title = "TIFF"
            val source = handle.jpegFile(frameName)
            val output = File(handle.exportsDir(), "$frameName.tif")
            enqueue(sessionId, title) {
                mutable.value = ExportState.Working(sessionId, title, "Converting", 0, 1)
                if (!source.exists()) throw ExportException("That frame's JPEG is missing.")
                val bitmap = decodeSoftware(source)
                val dims = "${bitmap.width}x${bitmap.height}"
                try {
                    BitmapTiff.write(bitmap, output)
                } finally {
                    bitmap.recycle()
                }
                ExportState.Done(
                    sessionId,
                    title,
                    output,
                    "image/tiff",
                    PublishKind.IMAGE,
                    "$dims, ${output.length() / BYTES_PER_MB} MB.",
                )
            }
        }

        fun cancel() {
            job?.cancel()
        }

        fun dismiss() {
            if (!busy) mutable.value = ExportState.Idle
        }

        /** Copies a finished export to Movies / Pictures / Downloads. Returns where it went. */
        fun saveToGallery(done: ExportState.Done): Uri =
            MediaStorePublisher(context).publish(done.file, done.file.name, done.mime, done.kind)

        /** A content URI another app may read, for the share sheet. */
        fun shareUri(done: ExportState.Done): Uri =
            FileProvider.getUriForFile(context, "${context.packageName}.reports", done.file)

        private fun begin(sessionId: String): SessionHandle? {
            if (busy) return null
            val handle = store.open(sessionId)
            if (handle == null) {
                mutable.value = ExportState.Failed(sessionId, "That session can't be found any more.")
                return null
            }
            handle.exportsDir().mkdirs()
            return handle
        }

        private fun enqueue(
            sessionId: String,
            title: String,
            work: suspend () -> ExportState,
        ) {
            mutable.value = ExportState.Working(sessionId, title, "Getting ready", 0, 0)
            ExportService.start(context)
            job =
                scope.launch {
                    mutable.value =
                        try {
                            work()
                        } catch (e: CancellationException) {
                            mutable.value = ExportState.Idle
                            throw e
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            ExportState.Failed(sessionId, e.message ?: "The export failed.")
                        }
                }
        }

        private fun jpegFrames(
            handle: SessionHandle,
            skipMoved: Boolean,
        ): List<File> =
            handle
                .usableFrames()
                .filter { it.hasJpeg && !(skipMoved && MOVED in it.flags) }
                .sortedBy { it.index }
                .map { handle.jpegFile(it.name) }
                .filter { it.exists() }

        private fun zipSources(
            handle: SessionHandle,
            includeRaw: Boolean,
        ): List<ZipSource> {
            val list = ArrayList<ZipSource>()
            handle.usableFrames().sortedBy { it.index }.forEach { frame ->
                if (frame.hasJpeg) {
                    handle.jpegFile(frame.name).takeIf { it.exists() }?.let {
                        list +=
                            ZipSource("jpeg/${frame.name}.jpg", it)
                    }
                }
                if (includeRaw &&
                    frame.hasDng
                ) {
                    handle.rawFile(frame.name).takeIf { it.exists() }?.let {
                        list +=
                            ZipSource("raw/${frame.name}.dng", it)
                    }
                }
            }
            val manifest = File(handle.dir, "session.json")
            if (manifest.exists()) list += ZipSource("session.json", manifest)
            return list
        }

        private fun decodeSoftware(file: File) =
            android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(file),
            ) { decoder, _, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }

        private fun phaseLabel(phase: ExportPhase): String =
            when (phase) {
                ExportPhase.PREPARING -> "Getting ready"
                ExportPhase.MEASURING -> "Measuring brightness"
                ExportPhase.ENCODING -> "Encoding video"
                ExportPhase.FINISHING -> "Finishing"
            }

        private companion object {
            const val MOVED = "moved"
            const val BYTES_PER_MB = 1_000_000L
        }
    }
