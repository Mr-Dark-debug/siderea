package io.github.mrdarkdebug.siderea.core.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Where an exported file is shown to the rest of the phone. */
enum class PublishKind(
    internal val collection: Uri,
    internal val folder: String,
) {
    VIDEO(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MOVIES),
    IMAGE(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES),
    DOWNLOAD(MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS),
}

/** Copies a finished export into the shared gallery / Downloads through MediaStore (no storage permission). */
class MediaStorePublisher(
    private val context: Context,
) {
    /** Returns the content URI of the published copy. The source file is left where it is. */
    fun publish(
        file: File,
        displayName: String,
        mime: String,
        kind: PublishKind,
    ): Uri {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${kind.folder}/$APP_FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri = checkNotNull(resolver.insert(kind.collection, values)) { "MediaStore refused $displayName" }
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private companion object {
        const val APP_FOLDER = "Siderea"
    }
}
