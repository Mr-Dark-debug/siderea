package io.github.mrdarkdebug.siderea

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import io.github.mrdarkdebug.siderea.core.data.settings.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Runs the Android Accessibility Test Framework over the main screens: touch-target size, missing labels, text
 * contrast and the like. It cannot replace trying the app with TalkBack, but it catches the mechanical problems.
 */
@OptIn(ExperimentalTestApi::class)
class AccessibilityTest {
    @get:Rule(order = 0)
    val retry = RetryRule()

    @get:Rule(order = 1)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun reset() {
        runBlocking { SettingsRepository(context.settingsDataStore()).reset() }
        rule.waitUntilAtLeastOneExists(hasContentDescription("Take photo"), timeoutMillis = 20_000)
    }

    private fun check() {
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theCameraScreenPassesTheChecks() {
        rule.enableAccessibilityChecks()
        check()
    }

    @Test
    fun everyModePanelPassesTheChecks() {
        rule.enableAccessibilityChecks()
        listOf(
            "TIMELAPSE" to "INTERVAL",
            "ASTRO" to "CAPTURE LENGTH",
            "BULB" to "TOTAL EXPOSURE",
        ).forEach {
            rule.onNodeWithText(it.first).performClick()
            rule.waitUntilAtLeastOneExists(hasText(it.second), 5_000)
            check()
        }
    }

    @Test
    fun theManualControlPanelsPassTheChecks() {
        rule.enableAccessibilityChecks()
        rule.onNodeWithText("Pro").performClick()
        listOf("SS", "ISO", "EV", "WB", "FOCUS").forEach { label ->
            rule.onNode(hasContentDescription("$label ", substring = true)).performClick()
            rule.waitForIdle()
            check()
            rule.onNodeWithText("DONE").performClick()
            rule.waitForIdle()
        }
    }

    @Test
    fun settingsAndTheInspectorPassTheChecks() {
        rule.enableAccessibilityChecks()
        rule.onNode(hasContentDescription("Settings")).performClick()
        rule.waitUntilAtLeastOneExists(hasText("DISPLAY"), 10_000)
        check()
        rule.onNodeWithText("Updates").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Automatic updates"), 5_000)
        check()
        rule.onNodeWithContentDescription("Back to settings").performClick()
    }
}
