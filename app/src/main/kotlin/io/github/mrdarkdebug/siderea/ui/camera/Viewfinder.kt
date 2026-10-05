package io.github.mrdarkdebug.siderea.ui.camera

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.mrdarkdebug.siderea.core.camera.analysis.FrameAnalysis
import io.github.mrdarkdebug.siderea.core.camera.engine.AspectRatio
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import io.github.mrdarkdebug.siderea.device.MotionState
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/** Size of the downscaled copy of the preview that is analysed, in pixels along the short side. */
private const val ANALYSIS_SHORT_SIDE = 180
private const val ANALYSIS_INTERVAL_MS = 160L
private const val NIGHT_VIEW_GAIN = 3f
private const val MAX_DISPLAY_GAIN = 16f

/**
 * The live camera image with every aid drawn over it. It owns the `TextureView` the camera writes to and
 * reports taps, the surface becoming available or going away, and small frames for analysis.
 */
@Composable
fun Viewfinder(
    state: CameraUiState,
    analysis: FrameAnalysis?,
    motion: MotionState,
    actions: CameraActions,
    onTexture: (SurfaceTexture) -> Unit,
    onTextureDestroyed: () -> Unit,
    onPixels: (IntArray, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aspect = state.aspect
    val ratio = if (aspect == AspectRatio.FOUR_THREE) PORTRAIT_FOUR_THREE else PORTRAIT_SIXTEEN_NINE
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    var tap by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var tapId by remember { mutableIntStateOf(0) }
    val gain = (state.displayGain * if (state.aids.nightView) NIGHT_VIEW_GAIN else 1f).coerceAtMost(MAX_DISPLAY_GAIN)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = min(maxWidth.value, maxHeight.value * ratio).dp
        val height = (width.value / ratio).dp
        Box(
            Modifier
                .size(width, height)
                .clip(SideriaShapes.medium)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val fx = offset.x / size.width
                        val fy = offset.y / size.height
                        tap = fx to fy
                        tapId++
                        actions.onTapFocus(fx, fy)
                        actions.onClosePanel()
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    TextureView(context).apply {
                        surfaceTextureListener =
                            object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    texture: SurfaceTexture,
                                    w: Int,
                                    h: Int,
                                ) = onTexture(texture)

                                override fun onSurfaceTextureSizeChanged(
                                    texture: SurfaceTexture,
                                    w: Int,
                                    h: Int,
                                ) = Unit

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                    onTextureDestroyed()
                                    // The ViewModel releases the texture once the camera has stopped writing to it.
                                    return false
                                }

                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                            }
                        textureView = this
                    }
                },
                update = { view -> applyDisplayGain(view, gain) },
            )
            GridOverlay(state.aids.grid)
            MaskOverlay(analysis, state.aids)
            if (state.aids.level) LevelOverlay(motion.horizonErrorDegrees, motion.elevationDegrees)
            FocusReticle(tap, tapId)
            if (state.aids.histogram) {
                HistogramOverlay(analysis, Modifier.align(Alignment.TopEnd).padding(SideriaSpacing.md))
            }
            val capture = state.capture
            if (capture is CaptureUi.Countdown) CountdownOverlay(capture.secondsLeft)
            ExposureBanner(state, Modifier.align(Alignment.TopCenter))
            if (state.engine is io.github.mrdarkdebug.siderea.core.camera.engine.EngineState.Opening) {
                Text(
                    "Opening camera…",
                    style = Siderea.text.readoutSmall,
                    color = Siderea.palette.onSurfaceMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.panel?.let { panel ->
                ControlPanelSheet(state, panel, actions, Modifier.align(Alignment.BottomCenter))
            }
            if (state.panel == null) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.lenses.filter { it.facing == state.lens?.facing }.forEach { lens ->
                        ChipButton(
                            lens.zoomLabel,
                            { actions.onSelectLens(lens.key) },
                            selected = lens.key == state.lens?.key,
                            enabled = !state.isCapturing,
                            description = "${lens.zoomLabel} lens",
                        )
                    }
                }
            }
        }
    }

    AnalysisPump(textureView, aspect, onPixels)
}

/** Polls a small bitmap of the preview a few times a second for the histogram, peaking and software AE. */
@Composable
private fun AnalysisPump(
    view: TextureView?,
    aspect: AspectRatio,
    onPixels: (IntArray, Int, Int) -> Unit,
) {
    val width = ANALYSIS_SHORT_SIDE
    val height = (ANALYSIS_SHORT_SIDE * aspect.value).toInt()
    LaunchedEffect(view, aspect) {
        val target = view ?: return@LaunchedEffect
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        while (true) {
            delay(ANALYSIS_INTERVAL_MS)
            if (target.isAvailable) {
                target.getBitmap(bitmap)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                onPixels(pixels.copyOf(), width, height)
            }
        }
    }
}

/** Brightens the viewfinder only: the saved photo never sees this. */
private fun applyDisplayGain(
    view: TextureView,
    gain: Float,
) {
    if (gain <= MIN_VISIBLE_GAIN) {
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        return
    }
    // Display luma is gamma encoded, so a linear-light gain becomes a smaller multiplier on the pixels.
    val k = gain.pow(1f / DISPLAY_GAMMA)
    val matrix = ColorMatrix().apply { setScale(k, k, k, 1f) }
    view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
}

private const val DISPLAY_GAMMA = 2.2f
private const val MIN_VISIBLE_GAIN = 1.01f
private const val PORTRAIT_FOUR_THREE = 3f / 4f
private const val PORTRAIT_SIXTEEN_NINE = 9f / 16f

/** "EXPOSING 7.2 / 16 s" with a thin progress bar, shown while a long exposure runs. */
@Composable
private fun ExposureBanner(
    state: CameraUiState,
    modifier: Modifier = Modifier,
) {
    val capture = state.capture as? CaptureUi.Exposing ?: return
    if (capture.durationNs < SHOW_FROM_NS) return
    val progress = rememberExposureProgress(capture) ?: 0f
    val palette = Siderea.palette
    val seconds = capture.durationNs / NS_PER_SECOND
    Box(
        modifier
            .fillMaxWidth()
            .padding(SideriaSpacing.md),
    ) {
        Text(
            text = "EXPOSING  ${"%.1f".format(
                java.util.Locale.ROOT,
                progress * seconds,
            )} / ${"%.0f".format(java.util.Locale.ROOT, seconds)} s",
            style = Siderea.text.caption,
            color = palette.accent,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress },
            color = palette.accent,
            trackColor = palette.surfaceRaised,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
        )
    }
}

private const val SHOW_FROM_NS = 700_000_000L
private const val NS_PER_SECOND = 1_000_000_000.0
