package io.github.mrdarkdebug.siderea.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaDimens
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import kotlin.math.roundToInt

/**
 * A horizontal ruler you swipe to choose one of [labels]: the current value in large mono text above a row
 * of ticks that slides under a fixed centre index, like the shutter and EV rulers of a pro camera.
 *
 * Every tick you cross gives a haptic detent, and the dial flings and snaps. It is also a proper
 * accessibility slider: TalkBack can read the value and step it up and down.
 *
 * @param majorEvery draw a taller tick every this many positions.
 */
@Composable
fun RulerDial(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    majorEvery: Int = DEFAULT_MAJOR_EVERY,
) {
    require(labels.isNotEmpty()) { "a dial needs at least one stop" }
    val palette = Siderea.palette
    val haptics = Siderea.haptics
    val lastIndex = labels.lastIndex
    val clamped = selectedIndex.coerceIn(0, lastIndex)
    val currentOnChange by rememberUpdatedState(onSelectedIndexChange)
    val currentSelected by rememberUpdatedState(clamped)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = clamped)
    val centered by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val itemSize =
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()
                    ?.size ?: 1
            (listState.firstVisibleItemIndex + if (offset * 2 >= itemSize) 1 else 0).coerceIn(0, lastIndex)
        }
    }

    // User dragging moves the value, with a detent on every tick.
    LaunchedEffect(listState) {
        snapshotFlow { centered }.collect { index ->
            if (listState.isScrollInProgress && index != currentSelected) {
                haptics.detent()
                currentOnChange(index)
            }
        }
    }
    // Programmatic changes (a preset, a reset) move the dial.
    LaunchedEffect(clamped) {
        if (!listState.isScrollInProgress && centered != clamped) listState.scrollToItem(clamped)
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    stateDescription = labels[clamped]
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(clamped.toFloat(), 0f..lastIndex.toFloat(), lastIndex - 1)
                    setProgress { target ->
                        if (!enabled) return@setProgress false
                        val index = target.roundToInt().coerceIn(0, lastIndex)
                        haptics.detent()
                        currentOnChange(index)
                        true
                    }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = labels[clamped],
            style = Siderea.text.readoutLarge,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
        )
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(RULER_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            val sidePadding = (maxWidth - TICK_SPACING) / 2
            LazyRow(
                state = listState,
                userScrollEnabled = enabled,
                flingBehavior = rememberSnapFlingBehavior(listState),
                contentPadding = PaddingValues(horizontal = sidePadding),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(labels) { index, _ ->
                    Tick(major = index % majorEvery == 0, selected = index == clamped)
                }
            }
            // The fixed centre index the ruler slides under.
            Canvas(Modifier.width(INDEX_WIDTH).height(RULER_HEIGHT)) {
                drawLine(
                    color = palette.accent,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = INDEX_STROKE.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun Tick(
    major: Boolean,
    selected: Boolean,
) {
    val palette = Siderea.palette
    Box(
        modifier =
            Modifier
                .width(TICK_SPACING)
                .height(RULER_HEIGHT),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.width(TICK_SPACING).height(RULER_HEIGHT)) {
            val x = size.width / 2
            val length = size.height * if (major) MAJOR_FRACTION else MINOR_FRACTION
            drawLine(
                color = if (selected) palette.accent else palette.onSurfaceMuted,
                start = Offset(x, size.height - length),
                end = Offset(x, size.height),
                strokeWidth = (if (major) MAJOR_STROKE else MINOR_STROKE).toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val DEFAULT_MAJOR_EVERY = 3
private const val DISABLED_ALPHA = 0.4f
private const val MAJOR_FRACTION = 0.7f
private const val MINOR_FRACTION = 0.4f
private val RULER_HEIGHT: Dp = 48.dp
private val TICK_SPACING: Dp = 16.dp
private val INDEX_WIDTH: Dp = SideriaSpacing.md
private val INDEX_STROKE: Dp = 2.dp
private val MAJOR_STROKE: Dp = 2.dp
private val MINOR_STROKE: Dp = SideriaDimens.hairline
