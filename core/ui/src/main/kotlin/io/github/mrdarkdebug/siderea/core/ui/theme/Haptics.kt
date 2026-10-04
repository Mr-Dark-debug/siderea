package io.github.mrdarkdebug.siderea.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Haptic vocabulary for the whole app. Respects the user's haptics setting in one place. */
@Immutable
class SideriaHaptics(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    /** A slider or dial passing a detent. */
    fun detent() = perform(HapticFeedbackType.SegmentFrequentTick)

    /** A discrete selection changed. */
    fun select() = perform(HapticFeedbackType.SegmentTick)

    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun confirm() = perform(HapticFeedbackType.Confirm)

    fun reject() = perform(HapticFeedbackType.Reject)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

val LocalSideriaHaptics =
    staticCompositionLocalOf<SideriaHaptics> {
        error("No SideriaHaptics provided. Wrap your UI in SideriaTheme.")
    }

@Composable
internal fun rememberSideriaHaptics(enabled: Boolean): SideriaHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled) { SideriaHaptics(feedback, enabled) }
}
