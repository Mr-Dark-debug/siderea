package io.github.mrdarkdebug.siderea

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    private fun sessionJsonFiles(): List<File> =
        sessionsRoot.walkTopDown().filter { it.name == "session.json" }.toList()

    private fun framesOnDisk(): Int =
        sessionsRoot.walkTopDown().count { it.extension == "jpg" && it.parentFile?.name == "jpeg" }
}
