package io.github.mrdarkdebug.siderea.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Siderea mark: a thin lens ring, a single star-trail arc inside it, ending in one dot.
 *
 * Geometry is the same as `branding/logo.svg` (512-unit grid), so the two never drift apart.
 */
@Composable
fun SideriaMark(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    ringColor: Color = Siderea.palette.onBackground,
    starColor: Color = Siderea.palette.accent,
    contentDescription: String? = null,
) {
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Canvas(modifier = modifier.size(size).then(semanticsModifier)) {
        val unit = this.size.minDimension / GRID
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Lens ring.
        drawCircle(
            color = ringColor,
            radius = RING_RADIUS * unit,
            center = center,
            style = Stroke(width = RING_STROKE * unit),
        )

        // Star trail: concentric with the ring, sweeping clockwise from START to END degrees.
        val arcRadius = ARC_RADIUS * unit
        drawArc(
            color = ringColor,
            startAngle = ARC_START_DEGREES,
            sweepAngle = ARC_END_DEGREES - ARC_START_DEGREES,
            useCenter = false,
            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
            size = Size(arcRadius * 2f, arcRadius * 2f),
            style = Stroke(width = ARC_STROKE * unit, cap = StrokeCap.Round),
        )

        // The star, at the end of the trail.
        val endAngle = Math.toRadians(ARC_END_DEGREES.toDouble())
        val star =
            Offset(
                x = center.x + (arcRadius * cos(endAngle)).toFloat(),
                y = center.y + (arcRadius * sin(endAngle)).toFloat(),
            )
        drawCircle(color = starColor, radius = STAR_RADIUS * unit, center = star)
    }
}

private const val GRID = 512f
private const val RING_RADIUS = 200f
private const val RING_STROKE = 22f
private const val ARC_RADIUS = 118f
private const val ARC_STROKE = 34f
private const val STAR_RADIUS = 32f
private const val ARC_START_DEGREES = 160f
private const val ARC_END_DEGREES = 330f
