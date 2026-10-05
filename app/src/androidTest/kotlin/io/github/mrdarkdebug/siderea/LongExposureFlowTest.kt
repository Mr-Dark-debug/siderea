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

/** A virtual bulb through the real UI: run until stopped, then combine the frames three ways. */
@OptIn(ExperimentalTestApi::class)
class LongExposureFlowTest {
    @get:Rule(order = 0)
    val retry = RetryRule()

    @get:Rule(order = 1)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.CAMERA,
            *(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()),
        )

    @get:Rule(order = 2)
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
        TimelapseService.stop(context)
        Thread.sleep(SETTLE_MS)
    }

    private fun files(
        folder: String,
        extension: String,
    ): List<File> =
        sessionsRoot.walkTopDown().filter { it.extension == extension && it.parentFile?.name == folder }.toList()

    @Test
    fun aBulbSessionRunsAndItsFramesCombineThreeWays() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Take photo"), timeoutMillis = 20_000)
        rule.onNodeWithText("BULB").performClick()
        rule.waitUntilAtLeastOneExists(hasText("TOTAL EXPOSURE"), 5_000)
        // The chip sits at the end of a scrolling row, so bring it into view first.
        rule.onNodeWithContentDescription("Until stopped").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Start long exposure").performClick()
        rule.waitUntilAtLeastOneExists(hasText("BEFORE YOU START"), 10_000)
        rule.onNodeWithText("Start").performClick()
        rule.waitUntilAtLeastOneExists(hasText("CAPTURING"), 20_000)
        rule.waitUntil(timeoutMillis = 60_000) { files("jpeg", "jpg").size >= 3 }
        rule.onNodeWithText("Stop").performClick()
        rule.waitUntilAtLeastOneExists(hasText("STOPPED"), 20_000)
        rule.onNodeWithText("Open session").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Status"), 10_000)

        for ((label, tag) in listOf("ADD LIGHT" to "additive", "KEEP BRIGHTEST" to "lighten", "AVERAGE" to "average")) {
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("Combine frames"))
            rule.onNodeWithText("Combine frames").performClick()
            rule.waitUntilAtLeastOneExists(hasText("COMBINE FRAMES"), 5_000)
            rule.onNodeWithText(label).performClick()
            rule.onNodeWithText("Start").performClick()
            rule.waitUntilAtLeastOneExists(hasText("LONG EXPOSURE READY"), 60_000)
            assertTrue("$tag result", files("exports", "jpg").any { it.name.contains(tag) })
            rule.onNodeWithText("Done").performScrollTo().performClick()
            rule.waitUntilDoesNotExist(hasText("LONG EXPOSURE READY"), 5_000)
        }
        assertTrue("TIFF written too", files("exports", "tif").size >= 3)
    }

    private companion object {
        const val SETTLE_MS = 3_000L
    }
}
