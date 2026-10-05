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
import io.github.mrdarkdebug.siderea.core.capture.timelapse.IntervalMath
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import kotlin.math.ceil

private val bulbChoices = listOf(30, 60, 120, 300, 600, 1_800, 3_600)
private const val PANEL_MAX_DP = 400
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val MS_PER_SECOND = 1_000L
private const val NS_PER_MS = 1_000_000L
private const val MIN_FRAMES = 2

/** How many frames a virtual bulb of [bulbSeconds] needs when each exposes for [exposureNs]. */
fun bulbFrames(
    bulbSeconds: Int,
    exposureNs: Long,
): Int {
    val exposureMs = (exposureNs / NS_PER_MS).coerceAtLeast(1)
    return ceil(bulbSeconds * MS_PER_SECOND / exposureMs.toDouble()).toInt().coerceAtLeast(MIN_FRAMES)
}

private const val MIN_USEFUL_FRAME_MS = 500L

private fun frameLabel(ms: Long): String = if (ms < MS_PER_SECOND) "$ms ms" else IntervalMath.formatSeconds(ms)

private fun bulbLabel(seconds: Int): String =
    when {
        seconds >= SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_HOUR} h"
        seconds >= SECONDS_PER_MINUTE -> "${seconds / SECONDS_PER_MINUTE} min"
        else -> "$seconds s"
    }

/**
 * Setup for a virtual bulb: one long exposure built from many short ones. The exposure of each frame comes from the
 * SS and ISO controls; this panel sets the total time.
 */
@Composable
fun BulbPanel(
    state: CameraUiState,
    actions: CameraActions,
) {
    val setup = state.timelapse
    val palette = Siderea.palette
    val interval = astroIntervalMs(state.effectiveShutterNs, 0L, state.overhead)
    val exposureMs = state.effectiveShutterNs / NS_PER_MS
    val untilStopped = setup.bulbSeconds == null
    val frames = setup.bulbSeconds?.let { bulbFrames(it, state.effectiveShutterNs) }
    val gapMs = (interval - exposureMs).coerceAtLeast(0)
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
                if (untilStopped) {
                    "Keeps exposing in ${frameLabel(exposureMs)} frames until you press stop."
                } else {
                    "$frames frames of ${frameLabel(exposureMs)}, " +
                        "about ${IntervalMath.formatSeconds(interval * (frames ?: 0))} on the clock"
                },
                style = Siderea.text.readoutSmall,
                color = palette.onBackground,
            )
            if (exposureMs < MIN_USEFUL_FRAME_MS) {
                Text(
                    if (state.limits?.manualExposure == true) {
                        "Each frame is only ${frameLabel(exposureMs)}. Set a longer exposure with SS, a few seconds " +
                            "or more, or this needs a huge number of frames."
                    } else {
                        "This camera has no manual shutter, so frames can't be made longer than automatic " +
                            "exposure allows. Try another lens."
                    },
                    style = Siderea.text.caption,
                    color = palette.danger,
                )
            }
            Text(
                "Set the length of each frame with SS and ISO below. Between frames the phone needs about " +
                    "${IntervalMath.formatSeconds(gapMs)} to save, so very fast moving lights can show tiny breaks.",
                style = Siderea.text.caption,
                color = palette.onSurfaceMuted,
            )
        }
        Text("TOTAL EXPOSURE", style = Siderea.text.caption, color = palette.onSurfaceMuted)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
        ) {
            bulbChoices.forEach { s ->
                ChipButton(
                    bulbLabel(s).uppercase(),
                    { actions.onTimelapse { it.copy(bulbSeconds = s) } },
                    selected = setup.bulbSeconds == s,
                    description = "Total exposure ${bulbLabel(s)}",
                )
            }
            ChipButton(
                "BULB",
                { actions.onTimelapse { it.copy(bulbSeconds = null) } },
                selected = untilStopped,
                description = "Until stopped",
            )
        }
        Text(
            "Afterwards open the session to add the frames together like one long exposure, keep the brightest " +
                "light, or average them.",
            style = Siderea.text.caption,
            color = palette.onSurfaceMuted,
        )
    }
}
