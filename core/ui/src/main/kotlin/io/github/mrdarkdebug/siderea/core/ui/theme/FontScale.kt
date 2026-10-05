package io.github.mrdarkdebug.siderea.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Limits how far the system font size grows inside [content]. The camera's own chrome (readouts, status line, mode
 * strip) has to share one row, so at the largest sizes it would wrap and break; capping it keeps it usable while
 * everything that has room to grow (panels, dialogs, session screens) still follows the system setting in full.
 */
@Composable
fun CappedFontScale(
    max: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val capped = Density(density.density, minOf(density.fontScale, max))
    CompositionLocalProvider(LocalDensity provides capped, content = content)
}
