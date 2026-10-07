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
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import io.github.mrdarkdebug.siderea.capture.TimelapseService
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStore
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import io.github.mrdarkdebug.siderea.core.data.settings.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class SkyTimelapseFlowTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.CAMERA,
            *(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()),
        )

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.getExternalFilesDir(null), "Siderea")

    @Before fun reset() {
        runBlocking { SettingsRepository(context.settingsDataStore()).reset() }
        root.deleteRecursively()
    }

    @After fun stop() {
        TimelapseService.stop(context)
    }

    @Test fun astroTimelapseRecordsLockedFramesAndExportsVideo() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Take photo"), 20_000)
        rule.onNodeWithText("TIMELAPSE").performClick()
        rule.onNodeWithContentDescription("Astro timelapse").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Night sky preset"), 5000)
        rule.onNodeWithContentDescription("Sky start delay 5 seconds. Tap to change.").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Delay 10s"), 5000)
        rule.onNodeWithContentDescription("Sky start delay 10 seconds. Tap to change.").performClick()
        rule.waitUntilAtLeastOneExists(hasText("No delay"), 5000)
        rule.onNodeWithText("5 min").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Start timelapse").performClick()
        rule.waitUntilAtLeastOneExists(hasText("BEFORE YOU START"), 10_000)
        rule.onNodeWithText("Start").performClick()
        rule.waitUntilAtLeastOneExists(hasText("CAPTURING"), 20_000)
        rule.waitUntil(60_000) {
            root.walkTopDown().count { it.extension == "jpg" && it.parentFile?.name == "jpeg" } >=
                3
        }
        rule.onNodeWithText("Stop").performClick()
        rule.waitUntilAtLeastOneExists(hasText("STOPPED"), 20_000)
        val store = SessionStore(root)
        val handle = store.open(store.list().first().id)!!
        assertEquals(SessionKind.ASTRO_TIMELAPSE, handle.manifest.kind)
        assertEquals(300_000L, handle.manifest.timelapse!!.durationMs)
        assertTrue(handle.manifest.timelapse!!.lockExposure)
        assertFalse(handle.manifest.timelapse!!.rampExposure)
        assertTrue(handle.frames.all { it.hasJpeg })
        rule.onNodeWithText("Open session").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Make video"), 10_000)
        rule.onNodeWithText("Make video").performClick()
        rule.waitUntilAtLeastOneExists(hasText("MAKE A VIDEO"), 5000)
        rule.onNodeWithText("Start").performClick()
        rule.waitUntilAtLeastOneExists(hasText("VIDEO READY"), 60_000)
        assertTrue(handle.exportsDir().listFiles()!!.any { it.extension == "mp4" && it.length() > 0 })
    }

    @Test fun settingsHasDedicatedUpdatesAndConfirmedReset() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Settings"), 20_000)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Updates").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Automatic updates"), 5000)
        rule.onNodeWithText("Installed", substring = true).assertExists()
        rule.onNodeWithContentDescription("Back to settings").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Settings"), 5000)
        rule.onNodeWithText("Reset settings").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Reset settings?"), 5000)
        rule.onNodeWithText("Cancel").performClick()
        rule.waitUntilDoesNotExist(hasText("Reset settings?"), 5000)
    }
}
