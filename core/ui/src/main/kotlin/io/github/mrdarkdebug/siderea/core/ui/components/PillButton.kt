package io.github.mrdarkdebug.siderea.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaDimens
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing

enum class PillStyle {
    /** Solid accent. One per screen at most: the primary action. */
    Filled,

    /** Accent outline, like the BKT / mode pill on a camera body. */
    Outlined,

    /** Quiet raised surface. */
    Subtle,
}

/** A large, full-radius button. 56dp tall by default so it can be hit one-handed in the dark. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Subtle,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = SideriaSpacing.xl),
) {
    val palette = Siderea.palette
    val haptics = Siderea.haptics
    val container =
        when (style) {
            PillStyle.Filled -> palette.accent
            PillStyle.Outlined -> palette.background
            PillStyle.Subtle -> palette.surfaceRaised
        }
    val content =
        when (style) {
            PillStyle.Filled -> palette.onAccent
            PillStyle.Outlined -> palette.accent
            PillStyle.Subtle -> palette.onBackground
        }
    val border = if (style == PillStyle.Outlined) BorderStroke(SideriaDimens.hairline, palette.accent) else null

    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = SideriaDimens.touchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(SideriaShapes.pill)
                .background(container)
                .then(if (border != null) Modifier.border(border, SideriaShapes.pill) else Modifier)
                .clickable(enabled = enabled, role = Role.Button) {
                    haptics.select()
                    onClick()
                }.padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Siderea.text.readoutSmall.copy(color = content),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

private const val DISABLED_ALPHA = 0.4f

/**
 * A compact, full-radius toggle chip for the viewfinder's top row and panels. 48dp tall (the Material
 * minimum), so it stays easy to hit even though it looks small.
 */
@Composable
fun ChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
) {
    val palette = Siderea.palette
    val haptics = Siderea.haptics
    val shape = SideriaShapes.pill
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = SideriaDimens.touchTargetMin, minHeight = SideriaDimens.touchTargetMin)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(shape)
                .background(if (selected) palette.accentContainer else palette.surfaceRaised)
                .then(if (selected) Modifier.border(SideriaDimens.hairline, palette.accent, shape) else Modifier)
                .semantics {
                    if (description != null) contentDescription = description
                    this.selected = selected
                }.clickable(enabled = enabled, role = Role.Button) {
                    haptics.select()
                    onClick()
                }.padding(horizontal = SideriaSpacing.sm + SideriaSpacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Siderea.text.caption,
            color = if (selected) palette.accent else palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
