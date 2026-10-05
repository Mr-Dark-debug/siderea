package io.github.mrdarkdebug.siderea.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.timelapse.IntervalMath
import io.github.mrdarkdebug.siderea.core.capture.timelapse.OverheadEstimate
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import java.util.Locale

private val astroFrameChoices = listOf(20, 40, 60, 100, 200, 400)
private val astroGapChoices = listOf(1_000L, 2_000L, 5_000L)
private const val PANEL_MAX_DP = 400
private const val MS_PER_SECOND = 1_000.0
private const val STARS_RULE = 500.0

/**
 * Setup for a sky session: long exposures back to back. The exposure itself comes from the SS and ISO controls;
 * this panel sets how many frames and the short gap between them, and says what the result will be.
 */
@Composable
fun AstroPanel(
    state: CameraUiState,
    actions: CameraActions,
) {
    val setup = state.timelapse
    val palette = Siderea.palette
    val exposureMs = state.effectiveShutterNs / NS_PER_MS
    val intervalMs = astroIntervalMs(state.effectiveShutterNs, setup.astroGapMs, state.overhead)
    val frames = setup.frameCount.takeIf { setup.stop == StopCondition.FRAME_COUNT }
    Column(
        Modifier
            .heightIn(max = PANEL_MAX_DP.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SideriaShapes.small)
                .background(palette.surfaceRaised)
                .padding(SideriaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
        ) {
            Text(
                "Exposure ${exposureLabel(exposureMs)} at ISO ${state.effectiveIso}, one frame every " +
                    IntervalMath.formatSeconds(intervalMs),
                style = Siderea.text.readoutSmall,
                color = palette.onBackground,
            )
            if (frames != null) {
                val total = intervalMs * frames
                Text(
                    "$frames frames, about ${IntervalMath.formatSeconds(total)} of sky",
                    style = Siderea.text.readoutSmall,
                    color = palette.onBackground,
                )
            }
            Text(
                "Set the exposure and ISO with SS and ISO below. Focus on a star and lock it, or set infinity.",
                style = Siderea.text.caption,
                color = palette.onSurfaceMuted,
            )
            trailingHint(state)?.let {
                Text(it, style = Siderea.text.caption, color = palette.onSurfaceMuted)
            }
        }

        Text("FRAMES", style = Siderea.text.caption, color = palette.onSurfaceMuted)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
        ) {
            astroFrameChoices.forEach { n ->
                ChipButton(
                    "$n",
                    { actions.onTimelapse { it.copy(stop = StopCondition.FRAME_COUNT, frameCount = n) } },
                    selected = setup.stop == StopCondition.FRAME_COUNT && setup.frameCount == n,
                    description = "$n frames",
                )
            }
            ChipButton(
                "UNTIL STOPPED",
                { actions.onTimelapse { it.copy(stop = StopCondition.UNTIL_STOPPED) } },
                selected = setup.stop == StopCondition.UNTIL_STOPPED,
            )
        }

        Text("GAP BETWEEN FRAMES", style = Siderea.text.caption, color = palette.onSurfaceMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs)) {
            astroGapChoices.forEach { gap ->
                ChipButton(
                    "${gap / MS_PER_SECOND.toLong()} s",
                    { actions.onTimelapse { it.copy(astroGapMs = gap) } },
                    selected = setup.astroGapMs == gap,
                    description = "Gap ${gap / MS_PER_SECOND.toLong()} seconds",
                )
            }
        }
        Text(
            "A short gap keeps star trails continuous. Dark frames are taken afterwards from the session screen, " +
                "with the lens covered.",
            style = Siderea.text.caption,
            color = palette.onSurfaceMuted,
        )
    }
}

/**
 * One frame every exposure plus the gap, but never less than the time the phone needs to save the last frame,
 * or the schedule would overrun on every frame.
 */
fun astroIntervalMs(
    exposureNs: Long,
    gapMs: Long,
    overhead: OverheadEstimate,
): Long = maxOf(exposureNs / NS_PER_MS + gapMs, IntervalMath.minimumIntervalMs(exposureNs, overhead))

private fun exposureLabel(ms: Long): String = if (ms >= MS_PER_SECOND) "${ms / MS_PER_SECOND.toLong()} s" else "$ms ms"

/** The "500 rule": roughly how long an exposure can be before stars stop being points. */
private fun trailingHint(state: CameraUiState): String? {
    val focal =
        state.lens
            ?.info
            ?.lens
            ?.equivalentFocalLengthsMm
            ?.firstOrNull() ?: return null
    if (focal <= 0f) return null
    val seconds = STARS_RULE / focal
    val exposure = state.effectiveShutterNs / MS_PER_SECOND / MS_PER_SECOND / MS_PER_SECOND
    val limit = "%.0f".format(Locale.ROOT, seconds)
    return if (exposure > seconds) {
        "Stars will start to streak in single frames beyond about $limit s on this lens (the 500 rule). " +
            "That suits star trails, not sharp stacks."
    } else {
        "Under about $limit s stars stay points on this lens (the 500 rule), good for stacking."
    }
}

private const val NS_PER_MS = 1_000_000L
