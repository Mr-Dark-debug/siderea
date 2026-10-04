package io.github.mrdarkdebug.siderea

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityJson
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import io.github.mrdarkdebug.siderea.core.data.settings.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** End-to-end UI checks that an emulator can answer: navigation, the inspector, settings. */
@OptIn(ExperimentalTestApi::class)
class SideriaSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { SettingsRepository(context.settingsDataStore()).reset() }
    }

    @Test
    fun homeShowsBrandAndHonestModeStatus() {
        rule.onNodeWithText("SIDEREA").assertExists()
        rule.onNodeWithText("Open Capability Inspector").assertExists()
        // Modes that do not exist yet must say so rather than pretend.
        rule.onNodeWithText("Not built yet · arrives in v0.2.0").assertExists()
    }

    @Test
    fun homeReadsTheCamerasOfThisPhone() {
        // Readouts expose one merged spoken description ("Longest shutter 1/2 s") rather than two texts.
        rule.waitUntilExactlyOneExists(
            hasContentDescription("Longest shutter", substring = true),
            timeoutMillis = 15_000,
        )
    }

    @Test
    fun inspectorListsDeviceAndCameras() {
        rule.onNodeWithText("Open Capability Inspector").performClick()
        rule.waitUntilExactlyOneExists(hasText("Copy as JSON"), timeoutMillis = 15_000)
        rule.onNodeWithText("Share report").assertExists()
        rule.onNodeWithText(Build.MODEL, substring = true).assertExists()
        rule.onNodeWithText("Android", substring = false).assertExists()
    }

    @Test
    fun copyAsJsonPutsAParsableReportOnTheClipboard() {
        rule.onNodeWithText("Open Capability Inspector").performClick()
        rule.waitUntilExactlyOneExists(hasText("Copy as JSON"), timeoutMillis = 15_000)
        rule.onNodeWithText("Copy as JSON").performClick()
        rule.waitForIdle()

        var text: String? = null
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.waitUntil(timeoutMillis = 10_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                text = clip?.getItemAt(0)?.text?.toString()
            }
            text?.contains("schemaVersion") == true
        }
        assertNotNull(text)
        val report = CapabilityJson.decode(text!!)
        assertEquals(Build.VERSION.SDK_INT, report.device.sdkInt)
        assertEquals(Build.MODEL, report.device.model)
    }

    @Test
    fun redModeToggleSurvivesActivityRecreation() {
        rule.onNode(hasContentDescription("Settings")).performClick()
        val redMode = hasText("Red night-vision mode") and isToggleable()
        rule.waitUntilExactlyOneExists(redMode, timeoutMillis = 5_000)
        rule.onNode(redMode).assertIsOff()
        rule.onNode(redMode).performClick()
        // The switch follows the stored value, which is written on an IO thread: wait, don't assume.
        rule.waitUntilToggle(redMode, on = true)

        rule.activityRule.scenario.recreate()
        rule.waitUntilExactlyOneExists(redMode, timeoutMillis = 5_000)
        rule.waitUntilToggle(redMode, on = true)
    }

    @Test
    fun resetSettingsRestoresDefaults() {
        rule.onNode(hasContentDescription("Settings")).performClick()
        val redMode = hasText("Red night-vision mode") and isToggleable()
        rule.waitUntilExactlyOneExists(redMode, timeoutMillis = 5_000)
        rule.onNode(redMode).performClick()
        rule.waitUntilToggle(redMode, on = true)
        rule.onNodeWithText("Reset settings").performScrollTo().performClick()
        rule.waitUntilToggle(redMode, on = false)
    }

    @Test
    fun settingsReachesTheInspector() {
        rule.onNode(hasContentDescription("Settings")).performClick()
        rule.waitUntilExactlyOneExists(hasText("Capability Inspector"), timeoutMillis = 5_000)
        rule.onNodeWithText("Capability Inspector").performClick()
        rule.waitUntilExactlyOneExists(hasText("Copy as JSON"), timeoutMillis = 15_000)
    }

    @Test
    fun settingsOpensTheGeneratedLicensesList() {
        rule.onNode(hasContentDescription("Settings")).performClick()
        rule.waitUntilExactlyOneExists(hasText("Open-source licenses"), timeoutMillis = 5_000)
        rule.onNodeWithText("Open-source licenses").performScrollTo().performClick()
        // We have left Settings, and the licenses screen has rendered its list without crashing.
        rule.waitUntilDoesNotExist(hasText("Reset settings"), timeoutMillis = 5_000)
        rule.onNode(hasContentDescription("Back")).assertExists()
    }

    private fun ComposeContentTestRule.waitUntilToggle(
        matcher: SemanticsMatcher,
        on: Boolean,
    ) {
        waitUntil(timeoutMillis = 5_000) {
            runCatching { if (on) onNode(matcher).assertIsOn() else onNode(matcher).assertIsOff() }.isSuccess
        }
    }
}
