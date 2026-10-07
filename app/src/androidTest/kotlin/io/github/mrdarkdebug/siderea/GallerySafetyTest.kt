package io.github.mrdarkdebug.siderea

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.capture.session.AppSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.CameraSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.DeviceSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.FrameRecord
import io.github.mrdarkdebug.siderea.core.capture.session.RequestedCapture
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SessionManifest
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStore
import io.github.mrdarkdebug.siderea.export.ExportCoordinator
import io.github.mrdarkdebug.siderea.ui.gallery.GalleryEdits
import io.github.mrdarkdebug.siderea.ui.gallery.GalleryPhoto
import io.github.mrdarkdebug.siderea.ui.gallery.GalleryRepository
import io.github.mrdarkdebug.siderea.ui.gallery.PhotoEdit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GallerySafetyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SessionStore(File(context.getExternalFilesDir(null), "Siderea"))
    private val repository = GalleryRepository(context, store, GalleryEdits(context), ExportCoordinator(context, store))
    private val sessions = mutableListOf<String>()
    private val owned = mutableListOf<Uri>()

    private fun fixture(status: SessionStatus) =
        store
            .create(SessionKind.ASTRO) { id, created ->
                SessionManifest(
                    id = id,
                    name = "Safety fixture",
                    kind = SessionKind.ASTRO,
                    status = status,
                    createdAtEpochMs = created,
                    app = AppSnapshot("Test", "1.2.0", 10200),
                    device = DeviceSnapshot("Fixture", "Fixture", "16", 36),
                    camera = CameraSnapshot("test", "test", null, "1x", "BACK"),
                    requested = RequestedCapture(CaptureSettings(), "FOUR_THREE"),
                )
            }.also { handle ->
                sessions += handle.id
                handle.jpegFile("IMG_000001").writeBytes(byteArrayOf(1, 2, 3))
                handle.rawFile("IMG_000001").writeBytes(byteArrayOf(4, 5, 6))
                handle.appendFrame(
                    FrameRecord(0, "IMG_000001", 0, System.currentTimeMillis(), hasJpeg = true, hasDng = true),
                )
                handle.writeManifest()
            }

    @After fun cleanup() {
        sessions.forEach(store::delete)
        owned.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test fun deletingSessionJPEGUpdatesJournalAndRetainsRAW() =
        runBlocking {
            val handle = fixture(SessionStatus.COMPLETED)
            val photo = repository.list().first { it.sessionId == handle.id && it.mime == "image/jpeg" }
            repository.delete(photo)
            assertFalse(handle.jpegFile("IMG_000001").exists())
            assertTrue(handle.rawFile("IMG_000001").isFile)
            assertFalse(
                store
                    .open(handle.id)!!
                    .frames
                    .single()
                    .hasJpeg,
            )
            assertTrue(
                store
                    .open(handle.id)!!
                    .frames
                    .single()
                    .hasDng,
            )
            assertTrue(repository.list().none { it.uri == photo.uri })
        }

    @Test fun runningSessionDeletionIsRejectedWithoutRemovingFiles() =
        runBlocking {
            val handle = fixture(SessionStatus.RUNNING)
            val photo = repository.list().first { it.sessionId == handle.id }
            assertTrue(runCatching { repository.delete(photo) }.isFailure)
            assertTrue(handle.jpegFile("IMG_000001").isFile)
            assertTrue(handle.rawFile("IMG_000001").isFile)
        }

    @Test fun processedPhotosAreListedAndCanBeRemovedIndependently() =
        runBlocking {
            val handle = fixture(SessionStatus.COMPLETED)
            val processed = File(handle.exportsDir(), "sky.jpg")
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            processed.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            val photo = repository.list().first { it.sessionId == handle.id && it.sessionExport }
            repository.delete(photo)
            assertFalse(processed.exists())
            assertTrue(handle.jpegFile("IMG_000001").isFile)
            assertTrue(
                store
                    .open(handle.id)!!
                    .frames
                    .single()
                    .hasJpeg,
            )
        }

    @Test fun galleryRejectsDeleteTargetsOutsideSidereaPictures() =
        runBlocking {
            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "OUTSIDE_GALLERY_TEST.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OutsideSiderea")
                    },
                )!!
            owned += uri
            assertTrue(
                runCatching {
                    repository.delete(
                        GalleryPhoto(uri.toString(), "OUTSIDE_GALLERY_TEST.jpg", 0, 0),
                    )
                }.isFailure,
            )
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)!!.use {
                assertTrue(it.count == 1)
            }
        }

    @Test fun editedOlderCaptureKeepsIndexedDateWithoutSourceOffset() =
        runBlocking {
            val handle = fixture(SessionStatus.COMPLETED)
            val taken = Instant.parse("2024-03-10T23:30:45.123Z").toEpochMilli()
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            val original = handle.jpegFile("IMG_000001")
            original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            ExifInterface(original).apply {
                setAttribute(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    Instant
                        .ofEpochMilli(taken)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")),
                )
                saveAttributes()
            }
            handle.appendFrame(FrameRecord(0, "IMG_000001", 0, taken, hasJpeg = true, hasDng = true))
            handle.writeManifest()
            val sourceBytes = original.readBytes()
            val source = repository.list().first { it.sessionId == handle.id && it.mime == "image/jpeg" }
            val copy = repository.saveCopy(source, PhotoEdit())
            val uri = Uri.parse(copy.uri)
            owned += uri
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(taken, it.getLong(0))
            }
            // The insertion URI uses external_primary; the library queries the aggregate external volume.
            assertEquals(taken, repository.list().first { it.name == copy.name }.capturedAt)
            context.contentResolver.openFileDescriptor(uri, "r")!!.use {
                assertTrue(ExifInterface(it.fileDescriptor).getAttribute("OffsetTimeOriginal") != null)
            }
            assertTrue(sourceBytes.contentEquals(original.readBytes()))
        }

    @Test fun legacyCopyWithoutIndexedDateUsesExifCaptureDate() =
        runBlocking {
            val taken = Instant.parse("2024-03-10T23:30:45.123Z")
            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "LEGACY_EDIT_TEST.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Siderea/Edits")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    },
                )!!
            owned += uri
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            context.contentResolver.openFileDescriptor(uri, "rw")!!.use {
                ExifInterface(it.fileDescriptor).apply {
                    setAttribute(
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        taken.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")),
                    )
                    setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIG, "123")
                    saveAttributes()
                }
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
            }
            assertEquals(taken.toEpochMilli(), repository.list().first { it.name == "LEGACY_EDIT_TEST.jpg" }.capturedAt)
        }
}
