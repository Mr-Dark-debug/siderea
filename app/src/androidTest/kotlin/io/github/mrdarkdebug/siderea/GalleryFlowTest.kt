package io.github.mrdarkdebug.siderea

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
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
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalTestApi::class)
class GalleryFlowTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val owned = mutableListOf<Uri>()
    private val store = SessionStore(File(context.getExternalFilesDir(null), "Siderea"))
    private var fixtureSession: String? = null
    private val galleryGrid =
        hasScrollAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Before fun createPhotos() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(60, 90, 120))
        listOf(LocalDate.now(), LocalDate.now().minusDays(1)).forEachIndexed { index, day ->
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "GALLERY_TEST_$index.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Siderea")
                    put(
                        MediaStore.Images.Media.DATE_TAKEN,
                        day
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    )
                    put(MediaStore.Images.Media.WIDTH, 400)
                    put(MediaStore.Images.Media.HEIGHT, 300)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            owned += uri
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            context.contentResolver.openFileDescriptor(uri, "rw")!!.use {
                val exif = ExifInterface(it.fileDescriptor)
                exif.setAttribute(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    day.atTime(12, 0).format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")),
                )
                exif.setAttribute(
                    ExifInterface.TAG_DATETIME,
                    day.atTime(12, 0).format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")),
                )
                exif.saveAttributes()
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                    put(
                        MediaStore.Images.Media.DATE_TAKEN,
                        day
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    )
                },
                null,
                null,
            )
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use {
                it.moveToFirst()
                android.util.Log.i("GalleryFixture", "name=$index expected=$day stored=${it.getLong(0)}")
            }
        }
        createYesterdaySession(bitmap)
        bitmap.recycle()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Gallery"), 20_000)
        rule.onNodeWithContentDescription("Gallery").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Gallery"), 10_000)
        if (rule.onAllNodesWithText("All dates").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("All dates").performClick()
        }
        rule.waitUntilAtLeastOneExists(hasText("Today"), 10_000)
    }

    private fun createYesterdaySession(bitmap: Bitmap) {
        // Private frames have authoritative journal timestamps; MediaStore scan timing is asynchronous.
        val taken =
            LocalDate
                .now()
                .minusDays(1)
                .atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val handle =
            store.create(SessionKind.TIMELAPSE) { id, _ ->
                SessionManifest(
                    id = id,
                    name = "Gallery test fixture",
                    kind = SessionKind.TIMELAPSE,
                    status = SessionStatus.COMPLETED,
                    createdAtEpochMs = taken,
                    app = AppSnapshot("Test fixture", "1.1.0", 10100),
                    device = DeviceSnapshot("Fixture", "Fixture", "16", 36),
                    camera = CameraSnapshot("test", "test", null, "1x", "BACK"),
                    requested = RequestedCapture(CaptureSettings(), "FOUR_THREE"),
                )
            }
        fixtureSession = handle.id
        handle.jpegFile("GALLERY_YESTERDAY").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        handle.appendFrame(
            FrameRecord(
                index = 0,
                name = "GALLERY_YESTERDAY",
                plannedAtMs = 0,
                capturedAtEpochMs = taken,
                hasJpeg = true,
                iso = 200,
                exposureNs = 100_000_000,
            ),
        )
        handle.finish(SessionStatus.COMPLETED)
    }

    @After fun cleanup() {
        owned.forEach { context.contentResolver.delete(it, null, null) }
        fixtureSession?.let(store::delete)
    }

    @Test fun timelineCalendarAndDetailsUseRealStoredDates() {
        rule.onNode(galleryGrid).performScrollToNode(hasText("Yesterday"))
        rule.onNodeWithText("Yesterday").assertExists()
        rule.onNodeWithContentDescription("Show calendar").performClick()
        val today = LocalDate.now().toString()
        rule.onNodeWithContentDescription("$today, has photos").performClick()
        rule.onNodeWithText("Yesterday").assertDoesNotExist()
        rule
            .onNode(
                galleryGrid,
            ).performScrollToNode(hasContentDescription("GALLERY_TEST_0.jpg", substring = true))
        rule.onNode(hasContentDescription("GALLERY_TEST_0.jpg", substring = true)).performClick()
        rule.onNodeWithContentDescription("Photo details").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Photo details"), 5_000)
        rule.onNodeWithText("GALLERY_TEST_0.jpg").assertExists()
        rule.onNodeWithText("400 × 300").assertExists()
    }

    @Test fun viewerSwipesAndSystemBackReturnsToGallery() {
        rule
            .onNode(
                galleryGrid,
            ).performScrollToNode(hasContentDescription("GALLERY_TEST_0.jpg", substring = true))
        rule.onNode(hasContentDescription("GALLERY_TEST_0.jpg", substring = true)).performClick()
        val before =
            rule
                .onNode(
                    hasText("/", substring = true),
                ).fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
                .text
        rule.onRoot().performTouchInput { swipeLeft() }
        val after =
            rule
                .onNode(
                    hasText("/", substring = true),
                ).fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
                .text
        assertNotEquals(before, after)
        rule.onNodeWithContentDescription("Photo details").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Photo details"), 5_000)
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.waitUntilDoesNotExist(hasText("Photo details"), 5_000)
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.waitUntilAtLeastOneExists(hasText("Gallery"), 5_000)
    }

    @Test fun galleryAndCalendarPassAccessibilityChecks() {
        rule.enableAccessibilityChecks()
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithContentDescription("Show calendar").performClick()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test fun newlyPublishedPhotoAppearsWithoutManualRefresh() {
        val uri =
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "GALLERY_LIVE.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Siderea")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            )!!
        owned += uri
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        rule.waitUntilAtLeastOneExists(hasContentDescription("GALLERY_LIVE.jpg", substring = true), 20_000)
    }
}
