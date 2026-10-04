package io.github.mrdarkdebug.siderea.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaDimens
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing

/** Flat, borderless top bar: a back target, a title, and any trailing actions. */
@Composable
fun SideriaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    navigationDescription: String = "",
    onNavigationClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = SideriaDimens.touchTarget)
                .padding(horizontal = SideriaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
    ) {
        if (navigationIcon != null) {
            IconTarget(icon = navigationIcon, description = navigationDescription, onClick = onNavigationClick)
        } else {
            Box(Modifier.size(SideriaSpacing.sm))
        }
        Text(
            text = title,
            style = Siderea.text.readout,
            color = Siderea.palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A square 56dp tap target holding one icon. Always carries a spoken description. */
@Composable
fun IconTarget(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = Siderea.haptics
    Box(
        modifier =
            modifier
                .size(SideriaDimens.touchTarget)
                .clip(SideriaShapes.pill)
                .semantics { contentDescription = description }
                .clickable(role = Role.Button) {
                    haptics.select()
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Siderea.palette.onBackground,
            modifier = Modifier.size(SideriaDimens.iconMedium),
        )
    }
}
