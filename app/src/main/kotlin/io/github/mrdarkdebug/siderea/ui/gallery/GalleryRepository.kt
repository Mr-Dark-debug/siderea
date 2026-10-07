package io.github.mrdarkdebug.siderea.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStore
import io.github.mrdarkdebug.siderea.export.ExportCoordinator
import io.github.mrdarkdebug.siderea.export.ExportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessions: SessionStore,
        private val edits: GalleryEdits,
        private val exports: ExportCoordinator,
    ) {
        private val thumbnails = LruCache<String, Bitmap>(THUMB_CACHE_COUNT)

        fun changes() =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            thumbnails.evictAll()
                            trySend(Unit)
                        }
                    }
                context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer,
                )
                awaitClose { context.contentResolver.unregisterContentObserver(observer) }
            }

        suspend fun list(): List<GalleryPhoto> =
            withContext(Dispatchers.IO) {
                (mediaPhotos() + sessionPhotos()).distinctBy { it.uri }.sortedByDescending { it.capturedAt }
            }

        private fun mediaPhotos(): List<GalleryPhoto> {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val columns =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.MIME_TYPE,
                )
            val selection =
                "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ? AND " +
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.IS_PENDING} = 0"
            return buildList {
                context.contentResolver
                    .query(
                        collection,
                        columns,
                        selection,
                        arrayOf(context.packageName, "Pictures/Siderea/%"),
                        "${MediaStore.Images.Media.DATE_ADDED} DESC",
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val taken =
                                c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)).takeIf { it > 0 }
                                    ?: (
                                        c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)) *
                                            MILLIS_PER_SECOND
                                    )
                            add(
                                GalleryPhoto(
                                    ContentUris.withAppendedId(collection, c.getLong(0)).toString(),
                                    c.getString(1),
                                    taken,
                                    c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
                                    c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)),
                                    c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)),
                                    c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)),
                                ),
                            )
                        }
                    }
            }
        }

        private fun sessionPhotos(): List<GalleryPhoto> =
            buildList {
                sessions.list().forEach { summary ->
                    val handle = sessions.open(summary.id) ?: return@forEach
                    handle.frames.forEach { frame ->
                        if (frame.hasJpeg) {
                            addSessionFile(
                                handle.jpegFile(frame.name),
                                summary.id,
                                frame.capturedAtEpochMs,
                                "image/jpeg",
                                frame.exposureNs,
                                frame.iso,
                            )
                        }
                        if (frame.hasDng) {
                            addSessionFile(
                                File(sessions.directory(summary.id), "raw/${frame.name}.dng"),
                                summary.id,
                                frame.capturedAtEpochMs,
                                "image/x-adobe-dng",
                                frame.exposureNs,
                                frame.iso,
                            )
                        }
                    }
                    handle
                        .exportsDir()
                        .listFiles { file -> file.extension.lowercase() in setOf("jpg", "jpeg", "png") }
                        .orEmpty()
                        .forEach { file ->
                            addSessionFile(
                                file,
                                summary.id,
                                summary.createdAtEpochMs,
                                if (file.extension.lowercase() ==
                                    "png"
                                ) {
                                    "image/png"
                                } else {
                                    "image/jpeg"
                                },
                                null,
                                null,
                                exported = true,
                            )
                        }
                }
            }

        private fun MutableList<GalleryPhoto>.addSessionFile(
            file: File,
            sessionId: String,
            taken: Long,
            mime: String,
            exposure: Long?,
            iso: Int?,
            exported: Boolean = false,
        ) {
            if (!file.isFile) return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
            add(
                GalleryPhoto(
                    uri.toString(),
                    file.name,
                    taken,
                    file.length(),
                    mime = mime,
                    sessionId = sessionId,
                    exposureNs = exposure,
                    iso = iso,
                    sessionExport = exported,
                ),
            )
        }

        suspend fun saveCopy(
            photo: GalleryPhoto,
            edit: PhotoEdit,
        ): GalleryPhoto {
            val current = list().firstOrNull { it.uri == photo.uri } ?: error("This photo is no longer available.")
            return edits.saveCopy(current, edit)
        }

        /** Revalidate every target against the library, never accept arbitrary provider or file paths. */
        suspend fun delete(photo: GalleryPhoto) =
            withContext(Dispatchers.IO) {
                val current = list().firstOrNull { it.uri == photo.uri } ?: error("This photo is no longer available.")
                val id = current.sessionId
                if (id != null) {
                    val working = exports.state.value as? ExportState.Working
                    check(working?.sessionId != id) { "Wait for this session's export to finish." }
                    val handle = sessions.open(id) ?: error("This session is no longer available.")
                    check(
                        handle.manifest.status !=
                            io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus.RUNNING,
                    ) {
                        "Stop or finalise this session before deleting its photos."
                    }
                    if (current.sessionExport) {
                        val file = File(handle.exportsDir(), current.name)
                        require(file.canonicalFile.parentFile == handle.exportsDir().canonicalFile)
                        check(file.isFile && file.delete()) { "Couldn't delete the processed photo." }
                    } else {
                        handle.removeMedia(
                            current.name.substringBeforeLast('.'),
                            raw =
                                current.mime == "image/x-adobe-dng",
                        )
                    }
                } else {
                    val uri = Uri.parse(current.uri)
                    val selection =
                        "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ? AND " +
                            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                    check(
                        context.contentResolver.delete(
                            uri,
                            selection,
                            arrayOf(context.packageName, "Pictures/Siderea/%"),
                        ) ==
                            1,
                    ) {
                        "Couldn't delete this photo."
                    }
                }
                thumbnails.remove(current.uri)
            }

        suspend fun image(
            photo: GalleryPhoto,
            full: Boolean = false,
        ): Bitmap? =
            withContext(Dispatchers.IO) {
                if (!full) thumbnails.get(photo.uri)?.let { return@withContext it }
                val uri = Uri.parse(photo.uri)
                val bitmap =
                    runCatching {
                        if (!full && photo.sessionId == null) {
                            context.contentResolver.loadThumbnail(uri, Size(THUMB_SIZE, THUMB_SIZE), null)
                        } else {
                            ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(context.contentResolver, uri),
                            ) { decoder, info, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                val edge = if (full) FULL_SIZE else THUMB_SIZE
                                val sample =
                                    (
                                        (
                                            maxOf(
                                                info.size.width,
                                                info.size.height,
                                            ) + edge - 1
                                        ) / edge
                                    ).coerceAtLeast(1)
                                decoder.setTargetSampleSize(sample)
                            }
                        }
                    }.getOrNull()
                if (!full && bitmap != null) thumbnails.put(photo.uri, bitmap)
                bitmap
            }

        suspend fun details(photo: GalleryPhoto): Map<String, String> =
            withContext(Dispatchers.IO) {
                val values = linkedMapOf<String, String>()
                runCatching {
                    context.contentResolver.openFileDescriptor(Uri.parse(photo.uri), "r")?.use { fd ->
                        val exif = ExifInterface(fd.fileDescriptor)
                        listOf(
                            "Camera" to ExifInterface.TAG_MODEL,
                            "Aperture" to ExifInterface.TAG_F_NUMBER,
                            "Exposure (s)" to ExifInterface.TAG_EXPOSURE_TIME,
                            "ISO" to ExifInterface.TAG_ISO_SPEED_RATINGS,
                            "Focal length" to ExifInterface.TAG_FOCAL_LENGTH,
                        ).forEach { (label, tag) ->
                            exif.getAttribute(tag)?.let { values[label] = it }
                        }
                    }
                }
                photo.iso?.let { values["ISO"] = it.toString() }
                photo.exposureNs?.let { values["Exposure (s)"] = (it.toDouble() / NS_PER_SECOND).toString() }
                values
            }

        private companion object {
            const val THUMB_CACHE_COUNT = 64
            const val THUMB_SIZE = 240
            const val FULL_SIZE = 2048
            const val MILLIS_PER_SECOND = 1000L
            const val NS_PER_SECOND = 1_000_000_000L
        }
    }
