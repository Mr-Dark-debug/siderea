package io.github.mrdarkdebug.siderea.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the UI may use. There is exactly one accent hue per palette, and no green or blue
 * anywhere in the red palette, so night vision survives a long session.
 *
 * Contrast of every text/background pair is asserted in `PaletteContrastTest`.
 */
@Immutable
data class SideriaPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val onBackground: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val danger: Color,
    val isRedMode: Boolean,
)

/** True-black, warm-white, amber accent. Default. */
val NightAmberPalette =
    SideriaPalette(
        background = Color(0xFF000000),
        surface = Color(0xFF0D0D0F),
        surfaceRaised = Color(0xFF18181B),
        outline = Color(0xFF2B2B30),
        onBackground = Color(0xFFECE9E3),
        onSurfaceMuted = Color(0xFF9A968F),
        accent = Color(0xFFF2A93B),
        onAccent = Color(0xFF1A1000),
        accentContainer = Color(0xFF2E2108),
        danger = Color(0xFFFF7A6B),
        isRedMode = false,
    )

/** Pure red-on-black for astronomy use: every colour sits in the red spectrum. */
val NightRedPalette =
    SideriaPalette(
        background = Color(0xFF000000),
        surface = Color(0xFF0C0202),
        surfaceRaised = Color(0xFF170504),
        outline = Color(0xFF3A110D),
        onBackground = Color(0xFFFF7560),
        onSurfaceMuted = Color(0xFFE04630),
        accent = Color(0xFFFF8C78),
        onAccent = Color(0xFF1A0300),
        accentContainer = Color(0xFF3A0F0A),
        danger = Color(0xFFFF9A88),
        isRedMode = true,
    )
