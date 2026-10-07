package io.github.mrdarkdebug.siderea.core.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSideriaPalette = staticCompositionLocalOf { NightAmberPalette }
val LocalSideriaText = staticCompositionLocalOf { SideriaTextStyles() }

/** Accessor for Siderea design tokens: `Siderea.palette.accent`, `Siderea.text.readout`. */
object Siderea {
    val palette: SideriaPalette
        @Composable @ReadOnlyComposable
        get() = LocalSideriaPalette.current

    val text: SideriaTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalSideriaText.current

    val haptics: SideriaHaptics
        @Composable @ReadOnlyComposable
        get() = LocalSideriaHaptics.current
}

/**
 * Siderea is dark-only: it is used outdoors at night. [redMode] swaps the amber palette for a
 * pure-red one that preserves dark adaptation.
 */
@Composable
fun SideriaTheme(
    redMode: Boolean = false,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = if (redMode) NightRedPalette else NightAmberPalette
    val colorScheme =
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentContainer,
            onPrimaryContainer = palette.accent,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.background,
            onSurface = palette.onBackground,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.onSurfaceMuted,
            surfaceContainerLowest = palette.background,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceRaised,
            surfaceContainerHighest = palette.surfaceRaised,
            outline = palette.onSurfaceMuted,
            outlineVariant = palette.outline,
            error = palette.danger,
            onError = palette.onAccent,
        )
    CompositionLocalProvider(
        LocalSideriaPalette provides palette,
        LocalSideriaText provides SideriaTextStyles(),
        LocalSideriaHaptics provides rememberSideriaHaptics(hapticsEnabled),
        LocalIndication provides ripple(color = palette.accent),
        LocalContentColor provides palette.onBackground,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SideriaTypography,
            content = content,
        )
    }
}
