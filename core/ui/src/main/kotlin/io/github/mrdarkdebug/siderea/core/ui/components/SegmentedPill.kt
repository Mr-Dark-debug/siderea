package io.github.mrdarkdebug.siderea.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaDimens
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing

/** A compact pill-shaped single-choice control, like the 3 / 5 / 7 bracket picker. */
@Composable
fun <T> SegmentedPill(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
) {
    val palette = Siderea.palette
    val haptics = Siderea.haptics
    Row(
        modifier =
            modifier
                .clip(SideriaShapes.pill)
                .background(palette.surfaceRaised)
                .padding(SideriaSpacing.xs)
                .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier =
                    Modifier
                        .defaultMinSize(
                            minWidth = SideriaDimens.touchTargetMin,
                            minHeight = SideriaDimens.touchTargetMin,
                        ).clip(SideriaShapes.pill)
                        .background(if (isSelected) palette.accentContainer else palette.surfaceRaised)
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.RadioButton) {
                            if (!isSelected) {
                                haptics.select()
                                onSelect(option)
                            }
                        }.padding(horizontal = SideriaSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = Siderea.text.readoutSmall,
                    color = if (isSelected) palette.accent else palette.onSurfaceMuted,
                )
            }
        }
    }
}
