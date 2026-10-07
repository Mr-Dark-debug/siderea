package io.github.mrdarkdebug.siderea.ui.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.sqrt

class GalleryEdits
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun saveCopy(
            photo: GalleryPhoto,
            edit: PhotoEdit,
        ): GalleryPhoto =
            withContext(Dispatchers.IO) {
                require(photo.editable) { "Edit the JPEG version of this photo. RAW originals stay untouched." }
                val resolver = context.contentResolver
                val sourceUri = Uri.parse(photo.uri)
                val metadata =
                    resolver
                        .openFileDescriptor(sourceUri, "r")
                        ?.use {
                            val exif = ExifInterface(it.fileDescriptor)
                            EXIF_TAGS
                                .mapNotNull { tag ->
                                    exif.getAttribute(tag)?.let { value -> tag to value }
                                }.toMap()
                        }.orEmpty()
                val source =
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, sourceUri)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val pixels = info.size.width.toDouble() * info.size.height
                        val scale =
                            min(
                                1.0,
                                min(
                                    sqrt(MAX_PIXELS / pixels),
                                    MAX_EDGE.toDouble() / maxOf(info.size.width, info.size.height),
                                ),
                            )
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                var output: Bitmap? = null
                var destination: Uri? = null
                var published = false
                try {
                    currentCoroutineContext().ensureActive()
                    val rendered = PhotoRenderer.render(source, edit)
                    output = rendered
                    val name = "SIDEREA_EDIT_${UUID.randomUUID()}.jpg"
                    val values =
                        ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Siderea/Edits")
                            put(MediaStore.Images.Media.DATE_TAKEN, photo.capturedAt)
                            put(MediaStore.Images.Media.WIDTH, rendered.width)
                            put(MediaStore.Images.Media.HEIGHT, rendered.height)
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    val uri =
                        resolver.insert(
                            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                            values,
                        )
                            ?: error("Couldn't create the edited copy. Check available storage.")
                    destination = uri
                    resolver.openOutputStream(uri, "w")?.use {
                        check(
                            rendered.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it),
                        ) { "Couldn't write the edited copy." }
                    } ?: error("Couldn't open the edited copy.")
                    resolver.openFileDescriptor(uri, "rw")?.use {
                        val exif = ExifInterface(it.fileDescriptor)
                        metadata.forEach { (tag, value) -> exif.setAttribute(tag, value) }
                        writeCaptureDate(exif, metadata, photo.capturedAt)
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                        exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, rendered.width.toString())
                        exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, rendered.height.toString())
                        exif.saveAttributes()
                    }
                    currentCoroutineContext().ensureActive()
                    check(
                        resolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                            null,
                            null,
                        ) ==
                            1,
                    )
                    published = true
                    val bytes = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                    GalleryPhoto(uri.toString(), name, photo.capturedAt, bytes, rendered.width, rendered.height)
                } finally {
                    source.recycle()
                    output?.recycle()
                    if (!published) destination?.let { resolver.delete(it, null, null) }
                }
            }

        private fun writeCaptureDate(
            exif: ExifInterface,
            metadata: Map<String, String>,
            capturedAt: Long,
        ) {
            if (metadata[ExifInterface.TAG_DATETIME_ORIGINAL] != null &&
                metadata[ExifInterface.TAG_OFFSET_TIME_ORIGINAL] != null
            ) {
                return
            }
            // MediaStore needs an explicit offset to retain old captures after scanning a new copy.
            val captured = Instant.ofEpochMilli(capturedAt).atZone(ZoneId.systemDefault())
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, captured.format(EXIF_DATE))
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, captured.format(EXIF_OFFSET))
            exif.setAttribute(
                ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                "%03d".format(Locale.ROOT, captured.nano / NANOS_PER_MILLISECOND),
            )
        }

        private companion object {
            const val MAX_PIXELS = 8_000_000.0
            const val MAX_EDGE = 4096
            const val JPEG_QUALITY = 95
            const val NANOS_PER_MILLISECOND = 1_000_000
            val EXIF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
            val EXIF_OFFSET: DateTimeFormatter = DateTimeFormatter.ofPattern("xxx", Locale.ROOT)
            val EXIF_TAGS =
                listOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_ISO_SPEED_RATINGS,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                )
        }
    }
