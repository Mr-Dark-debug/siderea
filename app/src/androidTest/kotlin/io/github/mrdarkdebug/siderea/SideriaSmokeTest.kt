package io.github.mrdarkdebug.siderea

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.MediaStore
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
import androidx.test.rule.GrantPermissionRule
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityJson
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import io.github.mrdarkdebug.siderea.core.data.settings.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** End-to-end UI checks that an emulator can answer: the camera screen, settings and the inspector. */
@OptIn(ExperimentalTestApi::class)
class SideriaSmokeTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun resetSettings() {
        runBlocking { SettingsRepository(context.settingsDataStore()).reset() }
    }

    private fun waitForCamera() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Take photo"), timeoutMillis = 20_000)
    }

    private fun readout(label: String) = hasContentDescription("$label ", substring = true)

    // ---- camera screen ---------------------------------------------------------------------------------

    @Test
    fun cameraShowsTheReadoutRowAndModeStrip() {
        waitForCamera()
        listOf("SS", "ISO", "EV", "WB", "FOCUS").forEach { rule.waitUntilAtLeastOneExists(readout(it), 10_000) }
        rule.onNodeWithText("PHOTO").assertExists()
        rule.onNodeWithText("LONG EXPOSURE").assertExists()
    }

    @Test
    fun modesThatAreNotBuiltYetSaySoInsteadOfPretending() {
        waitForCamera()
        rule.onNodeWithText("ASTRO").performClick()
        rule.waitUntilAtLeastOneExists(hasText("isn't built yet", substring = true), 5_000)
    }

    @Test
    fun tappingAReadoutOpensItsPanelAndDoneClosesIt() {
        waitForCamera()
        rule.waitUntilAtLeastOneExists(readout("SS"), 10_000)
        rule.onNode(readout("SS")).performClick()
        rule.waitUntilExactlyOneExists(hasText("SHUTTER"), 5_000)
        rule.onNodeWithText("DONE").performClick()
        rule.waitUntilDoesNotExist(hasText("SHUTTER"), 5_000)
    }

    @Test
    fun theAidsPanelTogglesTheGrid() {
        waitForCamera()
        rule.onNode(hasContentDescription("Viewfinder aids", substring = true)).performClick()
        rule.waitUntilExactlyOneExists(hasText("VIEWFINDER AIDS"), 5_000)
        rule.onNodeWithText("THIRDS").performClick()
        rule.onNodeWithText("DONE").performClick()
    }

    @Test
    fun takingAPhotoSavesItToTheGallery() {
        waitForCamera()
        val before = countSidereaPhotos()
        rule.onNode(hasContentDescription("Take photo")).performClick()
        rule.waitUntil(timeoutMillis = 30_000) { countSidereaPhotos() > before }
        assertTrue("a new photo should be in Pictures/Siderea", countSidereaPhotos() > before)
        rule.waitUntilAtLeastOneExists(hasContentDescription("Last photo", substring = true), 10_000)
        deleteSidereaPhotos()
    }

    private fun countSidereaPhotos(): Int =
        context.contentResolver
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("%Pictures/Siderea%"),
                null,
            )?.use { it.count } ?: 0

    private fun deleteSidereaPhotos() {
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("%Pictures/Siderea%"),
        )
    }

    // ---- settings and inspector ------------------------------------------------------------------------

    private fun openSettings() {
        waitForCamera()
        rule.onNode(hasContentDescription("Settings")).performClick()
    }

    @Test
    fun inspectorListsDeviceAndCameras() {
        openSettings()
        rule.waitUntilExactlyOneExists(hasText("Capability Inspector"), 5_000)
        rule.onNodeWithText("Capability Inspector").performClick()
        rule.waitUntilExactlyOneExists(hasText("Copy as JSON"), 15_000)
        rule.onNodeWithText("Share report").assertExists()
        rule.onNodeWithText(Build.MODEL, substring = true).assertExists()
    }

    @Test
    fun copyAsJsonPutsAParsableReportOnTheClipboard() {
        openSettings()
        rule.waitUntilExactlyOneExists(hasText("Capability Inspector"), 5_000)
        rule.onNodeWithText("Capability Inspector").performClick()
        rule.waitUntilExactlyOneExists(hasText("Copy as JSON"), 15_000)
        rule.onNodeWithText("Copy as JSON").performClick()
        rule.waitForIdle()

        var text: String? = null
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
        openSettings()
        val redMode = hasText("Red night-vision mode") and isToggleable()
        rule.waitUntilExactlyOneExists(redMode, timeoutMillis = 5_000)
        rule.onNode(redMode).assertIsOff()
        rule.onNode(redMode).performClick()
        // The switch follows the stored value, which is written on an IO thread: wait, don't assume.
        rule.waitUntilToggle(redMode, on = true)

        rule.activityRule.scenario.recreate()
        rule.waitUntilExactlyOneExists(redMode, timeoutMillis = 10_000)
        rule.waitUntilToggle(redMode, on = true)
    }

    @Test
    fun resetSettingsRestoresDefaults() {
        openSettings()
        val redMode = hasText("Red night-vision mode") and isToggleable()
        rule.waitUntilExactlyOneExists(redMode, timeoutMillis = 5_000)
        rule.onNode(redMode).performClick()
        rule.waitUntilToggle(redMode, on = true)
        rule.onNodeWithText("Reset settings").performScrollTo().performClick()
        rule.waitUntilToggle(redMode, on = false)
    }

    @Test
    fun settingsOpensTheGeneratedLicensesList() {
        openSettings()
        rule.waitUntilExactlyOneExists(hasText("Open-source licenses"), timeoutMillis = 5_000)
        rule.onNodeWithText("Open-source licenses").performScrollTo().performClick()
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
