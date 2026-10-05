package io.github.mrdarkdebug.siderea

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import io.github.mrdarkdebug.siderea.capture.TimelapseService
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import io.github.mrdarkdebug.siderea.core.data.settings.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The sky workflow through the real UI on the emulator: an Astro session, star trails from it, dark frames, and
 * a stack attempt. The emulator's virtual room has no stars, so stacking is expected to say so; real alignment is
 * covered by the synthetic-sky unit tests.
 */
@OptIn(ExperimentalTestApi::class)
class AstroFlowTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.CAMERA,
            *(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()),
        )

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sessionsRoot = File(context.getExternalFilesDir(null), "Siderea")

    @Before
    fun reset() {
        runBlocking { SettingsRepository(context.settingsDataStore()).reset() }
        sessionsRoot.deleteRecursively()
    }

    @After
    fun releaseTheCamera() {
        // A finished test may leave the service winding down; the next test must find the camera free.
        TimelapseService.stop(context)
        Thread.sleep(SETTLE_MS)
    }

    private fun files(
        folder: String,
        extension: String,
    ): List<File> =
        sessionsRoot.walkTopDown().filter { it.extension == extension && it.parentFile?.name == folder }.toList()

    private fun startAstroSession(frames: Int) {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Take photo"), timeoutMillis = 20_000)
        rule.onNodeWithText("ASTRO").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Start astro session"), 5_000)
        rule.waitUntilAtLeastOneExists(hasText("GAP BETWEEN FRAMES"), 5_000)
        rule.onNodeWithContentDescription("Gap 1 seconds").performClick()
        rule.onNodeWithContentDescription("Start astro session").performClick()
        rule.waitUntilAtLeastOneExists(hasText("BEFORE YOU START"), 10_000)
        rule.onNodeWithText("Start").performClick()
        rule.waitUntilAtLeastOneExists(hasText("CAPTURING"), 20_000)
        rule.waitUntil(timeoutMillis = 60_000) { files("jpeg", "jpg").size >= frames }
        rule.onNodeWithText("Stop").performClick()
        rule.waitUntilAtLeastOneExists(hasText("STOPPED"), 20_000)
        rule.onNodeWithText("Open session").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Status"), 10_000)
    }

    @Test
    fun astroPanelExplainsTheExposureAndTheGap() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Take photo"), timeoutMillis = 20_000)
        rule.onNodeWithText("ASTRO").performClick()
        rule.waitUntilAtLeastOneExists(hasText("one frame every", substring = true), 5_000)
        rule.waitUntilAtLeastOneExists(hasText("FRAMES"), 5_000)
        rule.waitUntilAtLeastOneExists(hasText("GAP BETWEEN FRAMES"), 5_000)
    }

    @Test
    fun anAstroSessionMakesStarTrailsAndTakesDarkFrames() {
        startAstroSession(frames = 3)

        // Star trails from the session's own frames.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Trails / stack"))
        rule.onNodeWithText("Trails / stack").performClick()
        rule.waitUntilAtLeastOneExists(hasText("SKY PROCESSING"), 5_000)
        rule.onNodeWithText("Start").performClick()
        rule.waitUntilAtLeastOneExists(hasText("STAR TRAILS READY"), 60_000)
        assertTrue("trails JPEG", files("exports", "jpg").isNotEmpty())
        assertTrue("trails TIFF", files("exports", "tif").isNotEmpty())
        rule.onNodeWithText("Done").performScrollTo().performClick()
        rule.waitUntilDoesNotExist(hasText("STAR TRAILS READY"), 5_000)

        // Dark frames: the dialog hands over to the camera screen while the service takes them.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Take darks"))
        rule.onNodeWithText("Take darks").performClick()
        rule.waitUntilAtLeastOneExists(hasText("DARK FRAMES"), 5_000)
        rule.onNodeWithText("Start").performClick()
        rule.waitUntil(timeoutMillis = 90_000) { files("darks", "jpg").size >= 10 }
        rule.waitUntilAtLeastOneExists(hasText("Back to camera"), 60_000)
        // The finished screen stays up until dismissed; leave the app as the next test expects it.
        rule.onNodeWithText("Back to camera").performClick()
        assertTrue("ten dark frames", files("darks", "jpg").size >= 10)
    }

    private companion object {
        const val SETTLE_MS = 3_000L
    }
}
