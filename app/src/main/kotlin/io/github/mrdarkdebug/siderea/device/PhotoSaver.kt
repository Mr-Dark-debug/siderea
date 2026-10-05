package io.github.mrdarkdebug.siderea.device

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mrdarkdebug.siderea.core.camera.engine.CapturedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

class SaveException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

/** Where a saved photo ended up. [primary] is the JPEG when there is one, otherwise the DNG. */
class SavedPhoto(
    val primary: Uri,
    val jpeg: Uri?,
    val dng: Uri?,
)

/**
 * Writes finished photos into the shared gallery (`Pictures/Siderea`) through MediaStore, which needs no
 * storage permission on Android 10+. The photo appears in other gallery apps as soon as it is written.
 */
@Singleton
class PhotoSaver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun save(photo: CapturedPhoto): SavedPhoto =
            withContext(Dispatchers.IO) {
                val base = "SIDEREA_" + LocalDateTime.now().format(STAMP)
                var jpegUri: Uri? = null
                var dngUri: Uri? = null
                try {
                    photo.jpeg?.let { bytes -> jpegUri = insert("$base.jpg", "image/jpeg") { it.write(bytes) } }
                    photo.dng?.let { file ->
                        dngUri =
                            insert("$base.dng", "image/x-adobe-dng") { out -> copy(file, out) }
                    }
                } finally {
                    photo.dng?.delete()
                }
                val primary = jpegUri ?: dngUri ?: throw SaveException("The camera returned no image to save.")
                SavedPhoto(primary, jpegUri, dngUri)
            }

        /** A small preview of a saved photo, or null if the system can't make one. */
        suspend fun thumbnail(
            uri: Uri,
            size: Int = THUMB,
        ): Bitmap? =
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(uri, Size(size, size), null) }.getOrNull()
            }

        private fun insert(
            name: String,
            mime: String,
            write: (java.io.OutputStream) -> Unit,
        ): Uri {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Siderea")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: throw SaveException("Android wouldn't create the photo file. Check that storage isn't full.")
            try {
                resolver.openOutputStream(uri)?.use(write)
                    ?: throw SaveException("Android wouldn't open the photo file for writing.")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                throw SaveException("Couldn't save the photo. Is the phone's storage full?", e)
            } catch (e: SaveException) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }

        private fun copy(
            file: File,
            out: java.io.OutputStream,
        ) {
            file.inputStream().buffered().use { it.copyTo(out) }
        }

        private companion object {
            val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
            const val THUMB = 160
        }
    }
