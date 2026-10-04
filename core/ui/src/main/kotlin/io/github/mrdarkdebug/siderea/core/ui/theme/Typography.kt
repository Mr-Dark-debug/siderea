package io.github.mrdarkdebug.siderea.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two voices, like a camera body: a quiet sans for words and a monospaced face for every number
 * the camera reports (shutter, ISO, EV). Mono digits do not jitter while values change.
 */
private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

@Immutable
data class SideriaTextStyles(
    /** Big hero value, e.g. the current shutter speed. */
    val readoutLarge: TextStyle =
        TextStyle(
            fontFamily = Mono,
            fontWeight = FontWeight.Medium,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.5).sp,
        ),
    /** Value in a readout cell, e.g. "1/250". */
    val readout: TextStyle =
        TextStyle(
            fontFamily = Mono,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
    val readoutSmall: TextStyle =
        TextStyle(
            fontFamily = Mono,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
    /** Small tracked label that sits under or over a value. Render it upper-case. */
    val caption: TextStyle =
        TextStyle(
            fontFamily = Mono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.2.sp,
        ),
)

internal val SideriaTypography =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Light,
                fontSize = 36.sp,
                lineHeight = 42.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.2.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.8.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 1.2.sp,
            ),
    )
