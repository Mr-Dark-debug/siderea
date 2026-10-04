package io.github.mrdarkdebug.siderea.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaDimens
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import java.util.Locale

/** Rounded surface with a hairline border. Clickable when [onClick] is given. */
@Composable
fun SideriaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = Siderea.palette
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(SideriaShapes.medium)
                .background(palette.surface)
                .border(SideriaDimens.hairline, palette.outline, SideriaShapes.medium)
                .then(clickable)
                .padding(SideriaSpacing.lg),
        content = content,
    )
}

/** Tracked caps label that introduces a group of rows. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = Siderea.text.caption,
        color = Siderea.palette.onSurfaceMuted,
        modifier = modifier.padding(top = SideriaSpacing.lg, bottom = SideriaSpacing.xs),
    )
}

/**
 * Label on the left, mono value on the right. Long values (capability lists, size lists) drop under
 * their label instead of wrapping into a ragged right-aligned column.
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueHighlighted: Boolean = false,
) {
    val palette = Siderea.palette
    val valueColor = if (valueHighlighted) palette.accent else palette.onBackground
    if (value.length > STACK_THRESHOLD) {
        Column(modifier = modifier.fillMaxWidth().padding(vertical = SideriaSpacing.xs)) {
            Text(text = label, style = Siderea.text.readoutSmall, color = palette.onSurfaceMuted)
            Text(text = value, style = Siderea.text.readoutSmall, color = valueColor)
        }
        return
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = SideriaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = Siderea.text.readoutSmall,
            color = palette.onSurfaceMuted,
            modifier = Modifier.weight(KEY_WEIGHT),
        )
        Text(
            text = value,
            style = Siderea.text.readoutSmall,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(VALUE_WEIGHT),
        )
    }
}

/**
 * YES / LIMITED / NO tag. State is carried by the word and the shape, never by colour alone:
 * filled = fully supported, outlined = partly, flat = not at all.
 */
@Composable
fun CapabilityChip(
    text: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    partial: Boolean = false,
) {
    val palette = Siderea.palette
    val shape = SideriaShapes.pill
    val textColor =
        when {
            on -> palette.onAccent
            partial -> palette.accent
            else -> palette.onSurfaceMuted
        }
    Text(
        text = text.uppercase(Locale.ROOT),
        style = Siderea.text.caption,
        color = textColor,
        modifier =
            modifier
                .clip(shape)
                .background(if (on) palette.accent else palette.surfaceRaised)
                .then(if (partial) Modifier.border(SideriaDimens.hairline, palette.accent, shape) else Modifier)
                .padding(horizontal = SideriaSpacing.md, vertical = SideriaSpacing.xs),
    )
}

private const val STACK_THRESHOLD = 26
private const val KEY_WEIGHT = 1f
private const val VALUE_WEIGHT = 1.4f
