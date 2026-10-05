package io.github.mrdarkdebug.siderea.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraFormat
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureScale
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.camera.control.WbMode
import io.github.mrdarkdebug.siderea.core.camera.control.WhiteBalance
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.RulerDial
import io.github.mrdarkdebug.siderea.core.ui.components.SegmentedPill
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the camera screen can ask for, so composables don't depend on the ViewModel directly. */
class CameraActions(
    val onShutter: () -> Unit,
    val onTogglePanel: (ControlPanel) -> Unit,
    val onClosePanel: () -> Unit,
    val onSelectLens: (String) -> Unit,
    val onFlip: () -> Unit,
    val onCycleFormat: () -> Unit,
    val onCycleAspect: () -> Unit,
    val onCycleTimer: () -> Unit,
    val onAids: ((Aids) -> Aids) -> Unit,
    val onShutterManual: (Boolean) -> Unit,
    val onIsoManual: (Boolean) -> Unit,
    val onShutterValue: (Long) -> Unit,
    val onIsoValue: (Int) -> Unit,
    val onEv: (Float) -> Unit,
    val onFocusManual: (Boolean) -> Unit,
    val onFocusValue: (Float) -> Unit,
    val onWhiteBalance: (WhiteBalance) -> Unit,
    val onSelectMode: (CameraMode) -> Unit,
    val onTapFocus: (Float, Float) -> Unit,
    val onSettings: () -> Unit,
    val onOpenLastPhoto: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onRetry: () -> Unit,
)

private const val EV_STEP = 1f / 3f
private const val KELVIN_MIN = 2000
private const val KELVIN_MAX = 10000
private const val KELVIN_STEP = 100
private const val TINT_MIN = -50
private const val TINT_MAX = 50
private const val TINT_STEP = 5

/** The panel that rises over the bottom of the viewfinder when a readout is tapped. */
@Composable
fun ControlPanelSheet(
    state: CameraUiState,
    panel: ControlPanel,
    actions: CameraActions,
    modifier: Modifier = Modifier,
) {
    val palette = Siderea.palette
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    palette.background.copy(alpha = PANEL_ALPHA),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ).padding(horizontal = SideriaSpacing.lg, vertical = SideriaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = panelTitle(panel),
                style = Siderea.text.caption,
                color = palette.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            ChipButton(
                text = "DONE",
                onClick = actions.onClosePanel,
                description = "Close the ${panelTitle(panel)} controls",
            )
        }
        val limits = state.limits
        when {
            panel == ControlPanel.AIDS -> AidsPanel(state.aids, actions)
            limits == null -> Hint("Reading this lens…")
            panel == ControlPanel.SHUTTER -> ShutterPanel(state, limits, actions)
            panel == ControlPanel.ISO -> IsoPanel(state, limits, actions)
            panel == ControlPanel.EV -> EvPanel(state, limits, actions)
            panel == ControlPanel.FOCUS -> FocusPanel(state, limits, actions)
            panel == ControlPanel.WB -> WhiteBalancePanel(state, actions)
        }
    }
}

private const val PANEL_ALPHA = 0.94f

private fun panelTitle(panel: ControlPanel) =
    when (panel) {
        ControlPanel.SHUTTER -> "SHUTTER"
        ControlPanel.ISO -> "ISO"
        ControlPanel.EV -> "EXPOSURE COMPENSATION"
        ControlPanel.FOCUS -> "FOCUS"
        ControlPanel.WB -> "WHITE BALANCE"
        ControlPanel.AIDS -> "VIEWFINDER AIDS"
    }

@Composable
private fun Hint(text: String) {
    Text(text, style = Siderea.text.readoutSmall, color = Siderea.palette.onSurfaceMuted)
}

@Composable
private fun AutoManualToggle(
    manual: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    SegmentedPill(
        options = listOf(false, true),
        selected = manual,
        onSelect = { if (enabled || !it) onChange(it) },
        label = { if (it) "MANUAL" else "AUTO" },
    )
}

@Composable
private fun ShutterPanel(
    state: CameraUiState,
    limits: ExposureLimits,
    actions: CameraActions,
) {
    val manual = state.settings.isShutterManual
    val stops = remember(limits) { ExposureScale.shutterStops(limits) }
    AutoManualToggle(manual, actions.onShutterManual, limits.manualExposure)
    if (!limits.manualExposure) {
        Hint(limits.manualExposureReason.orEmpty())
    } else if (manual) {
        RulerDial(
            labels = stops.map(CameraFormat::shutterCompact),
            selectedIndex = ExposureScale.nearestShutterIndex(stops, state.settings.shutterNs),
            onSelectedIndexChange = { actions.onShutterValue(stops[it]) },
            description = "Shutter speed",
        )
        if (state.settings.shutterNs > LONG_HINT_NS) {
            val longText = CameraFormat.exposure(state.settings.shutterNs)
            Hint(
                "The viewfinder previews at a shorter exposure and brightens to match. " +
                    "The photo uses $longText.",
            )
        }
    } else {
        Hint("Automatic: ${CameraFormat.shutterCompact(state.effectiveShutterNs)} right now.")
    }
}

private const val LONG_HINT_NS = 500_000_000L

@Composable
private fun IsoPanel(
    state: CameraUiState,
    limits: ExposureLimits,
    actions: CameraActions,
) {
    val manual = state.settings.isIsoManual
    val stops = remember(limits) { ExposureScale.isoStops(limits) }
    AutoManualToggle(manual, actions.onIsoManual, limits.manualExposure)
    if (!limits.manualExposure) {
        Hint(limits.manualExposureReason.orEmpty())
    } else if (manual) {
        RulerDial(
            labels = stops.map(Int::toString),
            selectedIndex = ExposureScale.nearestIsoIndex(stops, state.settings.iso),
            onSelectedIndexChange = { actions.onIsoValue(stops[it]) },
            description = "ISO sensitivity",
        )
    } else {
        Hint("Automatic: ISO ${state.effectiveIso} right now.")
    }
}

@Composable
private fun EvPanel(
    state: CameraUiState,
    limits: ExposureLimits,
    actions: CameraActions,
) {
    val applies = CameraSettingsOps.evApplies(state.settings)
    val steps =
        remember(limits) {
            val count = ((limits.evMaxStops - limits.evMinStops) / EV_STEP).roundToInt()
            List(count + 1) { limits.evMinStops + it * EV_STEP }
        }
    val index = steps.indices.minByOrNull { abs(steps[it] - state.settings.evStops) } ?: 0
    RulerDial(
        labels = steps.map(CameraFormat::evLabel),
        selectedIndex = index,
        onSelectedIndexChange = { actions.onEv(steps[it]) },
        description = "Exposure compensation",
        enabled = applies,
    )
    if (!applies) Hint("Compensation isn't used when both shutter and ISO are manual.")
}

@Composable
private fun FocusPanel(
    state: CameraUiState,
    limits: ExposureLimits,
    actions: CameraActions,
) {
    val manual = state.settings.focusMode == FocusMode.MANUAL
    val stops = remember(limits) { ExposureScale.focusStops(limits) }
    AutoManualToggle(manual, actions.onFocusManual, limits.manualFocus)
    if (!limits.manualFocus) {
        Hint(limits.manualFocusReason.orEmpty())
    } else if (manual) {
        RulerDial(
            labels = stops.map(CameraFormat::focusLabel),
            selectedIndex = ExposureScale.nearestFocusIndex(stops, state.settings.focusDiopters),
            onSelectedIndexChange = { actions.onFocusValue(stops[it]) },
            description = "Focus distance",
            majorEvery = 4,
        )
        Hint(
            if (limits.focusNearIsAssumed) {
                "∞ is the far end. This camera doesn't report its nearest focus, so Siderea assumes 10 cm."
            } else {
                "∞ is the far end of the dial. Turn on focus peaking in the aids to judge sharpness."
            },
        )
    } else {
        Hint("Autofocus is on. Tap the viewfinder to focus on a point.")
    }
}

private val whiteBalanceChoices =
    listOf(
        WbMode.AUTO to "AUTO",
        WbMode.DAYLIGHT to "DAY",
        WbMode.CLOUDY to "CLOUD",
        WbMode.TUNGSTEN to "TUNGSTEN",
        WbMode.FLUORESCENT to "FLUOR",
        WbMode.SHADE to "SHADE",
        WbMode.KELVIN to "KELVIN",
    )

@Composable
private fun WhiteBalancePanel(
    state: CameraUiState,
    actions: CameraActions,
) {
    val wb = state.settings.whiteBalance
    LazyRow(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
        items(whiteBalanceChoices) { (mode, label) ->
            ChipButton(
                text = label,
                selected = wb.mode == mode,
                onClick = { actions.onWhiteBalance(wb.copy(mode = mode)) },
            )
        }
    }
    if (wb.mode == WbMode.KELVIN) {
        val kelvins = remember { (KELVIN_MIN..KELVIN_MAX step KELVIN_STEP).toList() }
        RulerDial(
            labels = kelvins.map { "${it}K" },
            selectedIndex = ((wb.kelvin - KELVIN_MIN) / KELVIN_STEP).coerceIn(0, kelvins.lastIndex),
            onSelectedIndexChange = { actions.onWhiteBalance(wb.copy(kelvin = kelvins[it])) },
            description = "Colour temperature in Kelvin",
            majorEvery = 5,
        )
        val tints = remember { (TINT_MIN..TINT_MAX step TINT_STEP).toList() }
        Text("TINT  green ← → magenta", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        RulerDial(
            labels = tints.map { if (it > 0) "+$it" else it.toString() },
            selectedIndex = ((wb.tint - TINT_MIN) / TINT_STEP).coerceIn(0, tints.lastIndex),
            onSelectedIndexChange = { actions.onWhiteBalance(wb.copy(tint = tints[it])) },
            description = "Tint, green to magenta",
            majorEvery = 2,
        )
    } else {
        Spacer(Modifier.height(SideriaSpacing.xs))
    }
}

@Composable
private fun AidsPanel(
    aids: Aids,
    actions: CameraActions,
) {
    Text("GRID", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
    SegmentedPill(
        options = GridMode.entries,
        selected = aids.grid,
        onSelect = { grid -> actions.onAids { it.copy(grid = grid) } },
        label = { it.name },
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
        item { ChipButton("LEVEL", { actions.onAids { it.copy(level = !it.level) } }, selected = aids.level) }
        item { ChipButton("PEAKING", { actions.onAids { it.copy(peaking = !it.peaking) } }, selected = aids.peaking) }
        item { ChipButton("ZEBRA", { actions.onAids { it.copy(zebra = !it.zebra) } }, selected = aids.zebra) }
        item {
            ChipButton(
                "HISTOGRAM",
                { actions.onAids { it.copy(histogram = !it.histogram) } },
                selected = aids.histogram,
            )
        }
    }
    Hint("Night view (the moon button) brightens the viewfinder only. Your photo is not changed.")
}
