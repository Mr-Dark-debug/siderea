package io.github.mrdarkdebug.siderea.ui.camera

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrdarkdebug.siderea.core.camera.analysis.FrameAnalysis
import io.github.mrdarkdebug.siderea.core.ui.components.StatusPill
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val GUIDE_ALPHA = 0.35f
private const val LEVEL_TOLERANCE_DEGREES = 1f
private const val BINS_SHOWN = 64
private const val LEVEL_MIN_ELEVATION = 60f

@Composable
fun GridOverlay(
    mode: GridMode,
    modifier: Modifier = Modifier,
) {
    if (mode == GridMode.OFF) return
    val color = Color.White.copy(alpha = GUIDE_ALPHA)
    Canvas(modifier.fillMaxSize()) {
        val stroke = 1.dp.toPx()
        if (mode == GridMode.THIRDS) {
            for (i in 1..2) {
                drawLine(color, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), stroke)
                drawLine(color, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), stroke)
            }
        } else {
            val c = Offset(size.width / 2, size.height / 2)
            val arm = 14.dp.toPx()
            drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), stroke)
            drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), stroke)
        }
    }
}

/**
 * A horizon line that tilts against the phone so it stays true to the world. It turns accent-coloured
 * within a degree of level. When the camera points near the sky a level means nothing, so the line is
 * hidden and only the camera's altitude is shown, which is what you aim by when shooting stars.
 */
@Composable
fun LevelOverlay(
    horizonErrorDegrees: Float,
    elevationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val palette = Siderea.palette
    val pointsAtSky = abs(elevationDegrees) > LEVEL_MIN_ELEVATION
    Box(modifier.fillMaxSize()) {
        if (!pointsAtSky) {
            val level = abs(horizonErrorDegrees) <= LEVEL_TOLERANCE_DEGREES
            val color = if (level) palette.accent else Color.White.copy(alpha = 0.8f)
            Canvas(Modifier.fillMaxSize()) {
                rotate(-horizonErrorDegrees, pivot = center) {
                    val gap = 36.dp.toPx()
                    val half = size.width * 0.28f
                    val stroke = 2.dp.toPx()
                    drawLine(
                        color,
                        Offset(center.x - half, center.y),
                        Offset(center.x - gap, center.y),
                        stroke,
                        StrokeCap.Round,
                    )
                    drawLine(
                        color,
                        Offset(center.x + gap, center.y),
                        Offset(center.x + half, center.y),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }
        }
        if (abs(elevationDegrees) > ALTITUDE_PILL_FROM) {
            StatusPill(
                text = "ALT ${elevationDegrees.roundToInt()}°",
                modifier = Modifier.align(Alignment.TopStart).offset(x = 12.dp, y = 12.dp),
            )
        }
    }
}

private const val ALTITUDE_PILL_FROM = 15f

/** Focus-peaking and zebra masks drawn over the preview. */
@Composable
fun MaskOverlay(
    analysis: FrameAnalysis?,
    aids: Aids,
    modifier: Modifier = Modifier,
) {
    if (analysis == null || (!aids.peaking && !aids.zebra)) return
    val accent = Siderea.palette.accent.toArgb()
    val bitmap = remember(analysis, aids.peaking, aids.zebra, accent) { maskBitmap(analysis, aids, accent) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
        modifier = modifier.fillMaxSize(),
    )
}

private fun maskBitmap(
    analysis: FrameAnalysis,
    aids: Aids,
    accentArgb: Int,
): Bitmap {
    val w = analysis.width
    val h = analysis.height
    val pixels = IntArray(w * h)
    val peaking = analysis.peaking.takeIf { aids.peaking }
    val zebra = analysis.zebra.takeIf { aids.zebra }
    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            if (peaking != null && peaking[i]) {
                pixels[i] = accentArgb
            } else if (zebra != null && zebra[i] && ((x + y) / 2) % 2 == 0) {
                pixels[i] = ZEBRA_STRIPE
            }
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

/** Semi-transparent white diagonal stripes. */
private const val ZEBRA_STRIPE = 0xA0FFFFFF.toInt()

/** A compact luminance histogram with clipped ends highlighted. */
@Composable
fun HistogramOverlay(
    analysis: FrameAnalysis?,
    modifier: Modifier = Modifier,
) {
    val palette = Siderea.palette
    Canvas(
        modifier
            .size(width = 96.dp, height = 44.dp)
            .alpha(if (analysis == null) 0f else 1f),
    ) {
        val bins = analysis?.histogram ?: return@Canvas
        val grouped =
            IntArray(BINS_SHOWN) { g ->
                val per = bins.size / BINS_SHOWN
                (0 until per).sumOf { bins[g * per + it] }
            }
        val peak = (grouped.maxOrNull() ?: 1).coerceAtLeast(1)
        val barWidth = size.width / BINS_SHOWN
        grouped.forEachIndexed { i, count ->
            // The tallest bars are tamed so a single huge spike (a black sky) doesn't flatten the rest.
            val height = size.height * (count.toFloat() / peak).coerceIn(0f, 1f).let { sqrt(it) }
            val clipped =
                (i == 0 && analysis.clippedLow > CLIP_WARN) || (i == BINS_SHOWN - 1 && analysis.clippedHigh > CLIP_WARN)
            drawRect(
                color = if (clipped) palette.danger else Color.White.copy(alpha = 0.85f),
                topLeft = Offset(i * barWidth, size.height - height),
                size = Size((barWidth - 0.5f).coerceAtLeast(1f), height),
            )
        }
    }
}

private const val CLIP_WARN = 0.01f

/** Ring that appears where the user tapped to focus and fades away. */
@Composable
fun FocusReticle(
    point: Pair<Float, Float>?,
    tapId: Int,
    modifier: Modifier = Modifier,
) {
    if (point == null) return
    val palette = Siderea.palette
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(tapId) {
        alpha.snapTo(1f)
        alpha.animateTo(0f, tween(durationMillis = 900, delayMillis = 500))
    }
    Canvas(modifier.fillMaxSize().alpha(alpha.value)) {
        val c = Offset(point.first * size.width, point.second * size.height)
        drawCircle(palette.accent, radius = 34.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun CountdownOverlay(
    secondsLeft: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = secondsLeft.toString(),
            style = Siderea.text.readoutLarge.copy(fontSize = 96.sp, lineHeight = 104.sp),
            color = Siderea.palette.accent,
        )
    }
}

/** Elapsed / total while the sensor integrates a long exposure. */
@Composable
fun rememberExposureProgress(capture: CaptureUi): Float? {
    val tick = remember { mutableLongStateOf(0L) }
    val exposing = capture as? CaptureUi.Exposing
    LaunchedEffect(exposing) {
        if (exposing?.startedAtElapsedMs == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { tick.longValue = it }
        }
    }
    if (exposing == null) return null
    val started = exposing.startedAtElapsedMs ?: return 0f
    // Reading the tick makes this recompose every frame while exposing.
    tick.longValue
    val elapsedMs = android.os.SystemClock.elapsedRealtime() - started
    return (elapsedMs * NS_PER_MS / exposing.durationNs.toFloat()).coerceIn(0f, 1f)
}

private const val NS_PER_MS = 1_000_000f
