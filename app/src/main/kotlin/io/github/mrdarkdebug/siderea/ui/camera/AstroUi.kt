package io.github.mrdarkdebug.siderea.ui.camera

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraFormat
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.capture.session.SkyPreset
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.timelapse.IntervalMath
import io.github.mrdarkdebug.siderea.core.capture.timelapse.OverheadEstimate
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing

@Composable
fun AstroPanel(
    state: CameraUiState,
    actions: CameraActions,
) {
    val setup = state.timelapse
    var advanced by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
        SkyPresetPicker(state, actions)
        SkySummary(state)
        Text("CAPTURE LENGTH", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (setup.skyPreset == SkyPreset.STAR_TRAILS) {
                listOf(5, 15, 30, 60).forEach { minutes ->
                    ChipButton("$minutes min", {
                        actions.onTimelapse {
                            it.copy(
                                stop = StopCondition.DURATION,
                                durationMs =
                                    minutes * MS_PER_MINUTE,
                            )
                        }
                    }, selected = setup.stop == StopCondition.DURATION && setup.durationMs == minutes * MS_PER_MINUTE)
                }
            } else {
                listOf(20, 40, 60, 100).forEach { frames ->
                    ChipButton(
                        "$frames",
                        {
                            actions.onTimelapse { it.copy(stop = StopCondition.FRAME_COUNT, frameCount = frames) }
                        },
                        selected = setup.stop == StopCondition.FRAME_COUNT && setup.frameCount == frames,
                        description = "$frames frames",
                    )
                }
            }
            ChipButton(
                "Until stopped",
                { actions.onTimelapse { it.copy(stop = StopCondition.UNTIL_STOPPED) } },
                selected =
                    setup.stop == StopCondition.UNTIL_STOPPED,
            )
        }
        ChipButton(if (advanced) "Hide advanced" else "Advanced", { advanced = !advanced }, selected = advanced)
        if (advanced) {
            Text(setup.skyPreset.hint, style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
            SkyAdvanced(state, actions)
        }
    }
}

@Composable
internal fun SkyPresetPicker(
    state: CameraUiState,
    actions: CameraActions,
    video: Boolean = false,
) {
    val presets = SkyPreset.entries.filter { !video || it == SkyPreset.NIGHT_SKY || it == SkyPreset.MILKY_WAY }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { preset ->
                    ChipButton(
                        preset.label,
                        { actions.onSkyPreset(preset) },
                        selected = state.timelapse.skyPreset == preset,
                        enabled = state.limits?.manualExposure == true,
                        description = "${preset.label} preset",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkySummary(state: CameraUiState) {
    val setup = SkyCapturePolicy.setup(state)
    val interval = setup.intervalMs
    val total =
        when (setup.stop) {
            StopCondition.FRAME_COUNT -> interval * setup.frameCount
            StopCondition.DURATION -> setup.durationMs
            StopCondition.UNTIL_STOPPED -> null
        }
    SideriaCard {
        Text(
            "${CameraFormat.shutterCompact(state.effectiveShutterNs)} · ISO ${state.effectiveIso}",
            style = Siderea.text.readout,
            color = Siderea.palette.onBackground,
        )
        Text(
            "${if (state.settings.focusMode == FocusMode.MANUAL) "Far focus" else "Auto focus"} · " +
                "${state.timelapse.skyDelaySeconds}s delay",
            style = Siderea.text.caption,
            color = Siderea.palette.onSurfaceMuted,
        )
        Text(
            "Every ${IntervalMath.formatSeconds(
                interval,
            )}${total?.let { " · ${IntervalMath.formatSeconds(it)} total" } ?: " · until stopped"}",
            style = Siderea.text.caption,
            color = Siderea.palette.onSurfaceMuted,
        )
        if (state.limits?.manualExposure != true) {
            Text(
                "Manual sky capture unavailable",
                color = Siderea.palette.accent,
                style = Siderea.text.caption,
            )
        } else if (state.effectiveShutterNs < state.timelapse.skyPreset.exposureNs) {
            Text(
                "Exposure limit applied",
                style = Siderea.text.caption,
                color = Siderea.palette.onSurfaceMuted,
            )
        }
    }
    Text(
        "Tripod · Keep the phone still",
        style = Siderea.text.caption,
        color = Siderea.palette.onSurfaceMuted,
    )
}

@Composable
internal fun SkyAdvanced(
    state: CameraUiState,
    actions: CameraActions,
) {
    Text("GAP BETWEEN FRAMES", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1_000L, 2_000L, 5_000L).forEach { gap ->
            ChipButton("${gap / 1000}s", {
                actions.onTimelapse { it.copy(astroGapMs = gap) }
            }, selected = state.timelapse.astroGapMs == gap, description = "Gap ${gap / 1000} seconds")
        }
    }
    Text("START DELAY", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        skyStartDelays.forEach { seconds ->
            ChipButton("${seconds}s", {
                actions.onTimelapse { it.copy(skyDelaySeconds = seconds) }
            }, selected = state.timelapse.skyDelaySeconds == seconds, description = "Sky start delay $seconds seconds")
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipButton(
            "Far focus",
            {
                actions.onFocusManual(true)
                actions.onFocusValue(0f)
            },
            selected =
                state.settings.focusMode == FocusMode.MANUAL,
            enabled = state.limits?.manualFocus == true,
        )
        ChipButton(
            "Auto focus",
            { actions.onFocusManual(false) },
            selected = state.settings.focusMode == FocusMode.AUTO,
        )
        ChipButton("Exposure", { actions.onTogglePanel(ControlPanel.SHUTTER) })
        ChipButton("ISO", { actions.onTogglePanel(ControlPanel.ISO) })
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipButton("Keep screen on", {
            actions.onTimelapse { it.copy(keepScreenOn = !it.keepScreenOn) }
        }, selected = state.timelapse.keepScreenOn)
        ChipButton("Sessions", actions.onOpenSessions)
    }
}

fun astroIntervalMs(
    exposureNs: Long,
    gapMs: Long,
    overhead: OverheadEstimate,
): Long = maxOf(exposureNs / NS_PER_MS + gapMs, IntervalMath.minimumIntervalMs(exposureNs, overhead))

private const val NS_PER_MS = 1_000_000L
private const val MS_PER_MINUTE = 60_000L
internal val skyStartDelays = listOf(0, 3, 5, 10)
