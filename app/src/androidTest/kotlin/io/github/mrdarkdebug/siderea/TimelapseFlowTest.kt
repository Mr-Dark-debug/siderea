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
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import io.github.mrdarkdebug.siderea.core.data.settings.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Drives the whole timelapse flow through the real UI on the emulator: pre-flight checklist, a running
 * session, stop and the session detail screen.
 */
@OptIn(ExperimentalTestApi::class)
class TimelapseFlowTest {
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

    private fun openTimelapse() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Take photo"), timeoutMillis = 20_000)
        rule.onNodeWithText("TIMELAPSE").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Start timelapse"), timeoutMillis = 5_000)
    }

    @Test
    fun timelapsePanelShowsTheCalculatorFirst() {
        openTimelapse()
        rule.waitUntilAtLeastOneExists(hasText("INTERVAL"), 5_000)
        rule.waitUntilAtLeastOneExists(hasText("Minimum interval", substring = true), 5_000)
    }

    @Test
    fun preflightCanBeCancelled() {
        openTimelapse()
        rule.onNodeWithContentDescription("Start timelapse").performClick()
        rule.waitUntilAtLeastOneExists(hasText("BEFORE YOU START"), 10_000)
        rule.onNodeWithText("Cancel").performClick()
        rule.waitUntilDoesNotExist(hasText("BEFORE YOU START"), 5_000)
    }

    @Test
    fun aSessionRunsStopsAndItsDetailOpens() {
        openTimelapse()
        rule.onNodeWithContentDescription("Start timelapse").performClick()
        rule.waitUntilAtLeastOneExists(hasText("BEFORE YOU START"), 10_000)
        rule.onNodeWithText("Start").performClick()

        rule.waitUntilAtLeastOneExists(hasText("CAPTURING"), 20_000)
        // Wait for at least one frame to be written before stopping.
        rule.waitUntil(timeoutMillis = 30_000) { framesOnDisk() >= 1 }
        rule.onNodeWithText("Stop").performClick()

        rule.waitUntilAtLeastOneExists(hasText("STOPPED"), 20_000)
        rule.onNodeWithText("Open session").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Status"), 10_000)
        rule.waitUntilAtLeastOneExists(hasText("STOPPED"), 5_000)
        assertTrue("session.json must exist on disk", sessionJsonFiles().isNotEmpty())
    }

    @Test
    fun aFinishedSessionExportsAVideoAndAZip() {
        openTimelapse()
        rule.onNodeWithContentDescription("Start timelapse").performClick()
        rule.waitUntilAtLeastOneExists(hasText("BEFORE YOU START"), 10_000)
        rule.onNodeWithText("Start").performClick()
        rule.waitUntilAtLeastOneExists(hasText("CAPTURING"), 20_000)
        rule.waitUntil(timeoutMillis = 40_000) { framesOnDisk() >= 2 }
        rule.onNodeWithText("Stop").performClick()
        rule.waitUntilAtLeastOneExists(hasText("STOPPED"), 20_000)
        rule.onNodeWithText("Open session").performClick()

        rule.waitUntilAtLeastOneExists(hasText("Make video"), 10_000)
        rule.onNodeWithText("Make video").performClick()
        rule.waitUntilAtLeastOneExists(hasText("MAKE A VIDEO"), 5_000)
        rule.onNodeWithText("Start").performClick()
        rule.waitUntilAtLeastOneExists(hasText("VIDEO READY"), 60_000)
        assertTrue("an mp4 must exist in exports/", exportedFiles("mp4").isNotEmpty())
        // The result card is taller than the room left below it, so bring its button into view first.
        rule.onNodeWithText("Done").performScrollTo().performClick()

        // Dismissing is asynchronous, and the list may be left scrolled past the buttons by the taller result card.
        rule.waitUntilDoesNotExist(hasText("VIDEO READY"), 5_000)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("ZIP frames"))
        rule.waitUntilAtLeastOneExists(hasText("ZIP frames"), 5_000)
        rule.onNodeWithText("ZIP frames").performClick()
        rule.waitUntilAtLeastOneExists(hasText("ZIP READY"), 30_000)
        assertTrue("a zip must exist in exports/", exportedFiles("zip").isNotEmpty())
    }

    private fun exportedFiles(extension: String): List<File> =
        sessionsRoot.walkTopDown().filter { it.extension == extension && it.parentFile?.name == "exports" }.toList()

    private fun sessionJsonFiles(): List<File> =
        sessionsRoot.walkTopDown().filter { it.name == "session.json" }.toList()

    private fun framesOnDisk(): Int =
        sessionsRoot.walkTopDown().count { it.extension == "jpg" && it.parentFile?.name == "jpeg" }
}
