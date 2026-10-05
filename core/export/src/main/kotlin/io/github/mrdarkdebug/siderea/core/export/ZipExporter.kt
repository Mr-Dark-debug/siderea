package io.github.mrdarkdebug.siderea.core.export

import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One file to put in a zip, under the path [name] inside the archive. */
data class ZipSource(
    val name: String,
    val file: File,
)

/** Zips a session's frames. Already-compressed files (JPEG, DNG, video) are stored, not squeezed again. */
object ZipExporter {
    private val STORED_EXTENSIONS = setOf("jpg", "jpeg", "dng", "mp4", "zip", "png")

    /**
     * Writes [sources] to [output]. [onProgress] gets `(done, total)` after every file. If anything fails the
     * partial archive is deleted so no half-written zip is left behind.
     */
    fun zip(
        sources: List<ZipSource>,
        output: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        output.parentFile?.mkdirs()
        try {
            ZipOutputStream(BufferedOutputStream(output.outputStream())).use { zip ->
                sources.forEachIndexed { index, source ->
                    val stored = source.file.extension.lowercase() in STORED_EXTENSIONS
                    zip.setLevel(if (stored) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
                    zip.putNextEntry(ZipEntry(source.name).apply { time = source.file.lastModified() })
                    source.file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    onProgress(index + 1, sources.size)
                }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            output.delete()
            throw e
        }
    }
}
