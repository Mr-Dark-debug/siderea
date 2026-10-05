package io.github.mrdarkdebug.siderea.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes

/**
 * The big round shutter. While a photo is being taken it shows a progress ring and becomes a stop button.
 *
 * @param progress 0..1 while exposing, or null when idle.
 */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    busyDescription: String? = null,
    enabled: Boolean = true,
) {
    val palette = Siderea.palette
    val haptics = Siderea.haptics
    val busy = progress != null
    Box(
        modifier =
            modifier
                .size(BUTTON_SIZE)
                .clip(SideriaShapes.pill)
                .semantics {
                    contentDescription = if (busy) busyDescription ?: description else description
                    if (busy) stateDescription = "${((progress ?: 0f) * PERCENT).toInt()} percent"
                }.clickable(enabled = enabled, role = Role.Button) {
                    if (busy) haptics.reject() else haptics.confirm()
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(BUTTON_SIZE)) {
            val ring = RING.toPx()
            val inset = ring / 2
            drawCircle(
                color = palette.onBackground,
                radius = size.minDimension / 2 - inset,
                style = Stroke(width = ring),
            )
            if (busy) {
                drawArc(
                    color = palette.accent,
                    startAngle = -QUARTER,
                    sweepAngle = FULL * (progress ?: 0f).coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - ring, size.height - ring),
                    style = Stroke(width = ring, cap = StrokeCap.Round),
                )
            }
            val core = size.minDimension / 2 - ring * CORE_GAP
            if (busy) {
                // A stop square while exposing.
                val side = core * STOP_SCALE
                drawRoundRect(
                    color = palette.accent,
                    topLeft = Offset(size.width / 2 - side / 2, size.height / 2 - side / 2),
                    size = Size(side, side),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(side * STOP_RADIUS),
                )
            } else {
                drawCircle(color = palette.onBackground, radius = core)
            }
        }
    }
}

private val BUTTON_SIZE: Dp = 76.dp
private val RING: Dp = 4.dp
private const val CORE_GAP = 2.2f
private const val STOP_SCALE = 1.1f
private const val STOP_RADIUS = 0.2f
private const val QUARTER = 90f
private const val FULL = 360f
private const val PERCENT = 100
