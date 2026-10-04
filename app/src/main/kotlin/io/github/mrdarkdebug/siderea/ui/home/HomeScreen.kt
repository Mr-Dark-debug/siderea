package io.github.mrdarkdebug.siderea.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.R
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilitySummary
import io.github.mrdarkdebug.siderea.core.ui.components.IconTarget
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.ReadoutBar
import io.github.mrdarkdebug.siderea.core.ui.components.SectionLabel
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaMark
import io.github.mrdarkdebug.siderea.core.ui.components.StatusPill
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing

/** What each mode is waiting on. Shown honestly: these modes do not exist yet. */
private data class PlannedMode(
    val nameRes: Int,
    val release: String,
)

private val plannedModes =
    listOf(
        PlannedMode(R.string.mode_photo, "v0.2.0"),
        PlannedMode(R.string.mode_timelapse, "v0.3.0"),
        PlannedMode(R.string.mode_astro, "v0.5.0"),
        PlannedMode(R.string.mode_long_exposure, "v0.6.0"),
    )

@Composable
fun HomeScreen(
    onOpenInspector: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onOpenInspector = onOpenInspector,
        onOpenSettings = onOpenSettings,
        onRetry = viewModel::retry,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenInspector: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bars = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = SideriaSpacing.gutter,
                end = SideriaSpacing.gutter,
                top = bars.calculateTopPadding() + SideriaSpacing.sm,
                bottom = nav.calculateBottomPadding() + SideriaSpacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.md),
    ) {
        item { Header(onOpenSettings) }
        item { Spacer(Modifier.height(SideriaSpacing.sm)) }
        item { SnapshotCard(state, onRetry) }
        item { SectionLabel(stringResource(R.string.home_modes_label)) }
        items(plannedModes, key = { it.release }) { mode -> ModeCard(mode) }
        item { Spacer(Modifier.height(SideriaSpacing.sm)) }
        item {
            PillButton(
                text = stringResource(R.string.home_open_inspector),
                onClick = onOpenInspector,
                style = PillStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.md),
    ) {
        SideriaMark()
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = Siderea.text.readout,
                color = Siderea.palette.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = Siderea.text.caption,
                color = Siderea.palette.onSurfaceMuted,
            )
        }
        IconTarget(
            icon = Icons.Default.Settings,
            description = stringResource(R.string.action_settings),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun SnapshotCard(
    state: HomeUiState,
    onRetry: () -> Unit,
) {
    SideriaCard {
        Text(
            text = stringResource(R.string.home_snapshot_label).uppercase(),
            style = Siderea.text.caption,
            color = Siderea.palette.onSurfaceMuted,
        )
        Spacer(Modifier.height(SideriaSpacing.sm))
        when (state) {
            HomeUiState.Loading -> {
                Text(
                    text = stringResource(R.string.home_reading),
                    style = Siderea.text.readoutSmall,
                    color = Siderea.palette.onSurfaceMuted,
                )
            }

            is HomeUiState.Failed -> {
                Text(
                    text = stringResource(R.string.home_failed_title),
                    style = Siderea.text.readout,
                    color = Siderea.palette.danger,
                )
                Spacer(Modifier.height(SideriaSpacing.xs))
                Text(state.message, style = Siderea.text.readoutSmall, color = Siderea.palette.onSurfaceMuted)
                Spacer(Modifier.height(SideriaSpacing.md))
                PillButton(
                    text = stringResource(R.string.action_retry),
                    onClick = onRetry,
                    style = PillStyle.Outlined,
                )
            }

            is HomeUiState.Ready -> {
                ReadySnapshot(state.summary)
            }
        }
    }
}

@Composable
private fun ReadySnapshot(summary: CapabilitySummary) {
    val yes = stringResource(R.string.home_yes)
    val no = stringResource(R.string.home_no)
    Text(summary.deviceName, style = Siderea.text.readout, color = Siderea.palette.onBackground)
    Text(
        text = "${summary.androidLabel} · " + cameraCountText(summary.cameraCount),
        style = Siderea.text.readoutSmall,
        color = Siderea.palette.onSurfaceMuted,
    )
    Spacer(Modifier.height(SideriaSpacing.sm))
    Text(
        text = stringResource(if (summary.manualSensor) R.string.home_manual_yes else R.string.home_manual_no),
        style = Siderea.text.readoutSmall,
        color = if (summary.manualSensor) Siderea.palette.accent else Siderea.palette.danger,
    )
    Spacer(Modifier.height(SideriaSpacing.md))
    ReadoutBar(
        items =
            listOf(
                stringResource(R.string.home_stat_shutter) to (summary.longestExposure ?: "—"),
                stringResource(R.string.home_stat_iso) to (summary.isoRange ?: "—"),
                stringResource(R.string.home_stat_raw) to if (summary.raw) yes else no,
            ),
    )
}

@Composable
private fun cameraCountText(count: Int): String =
    if (count ==
        1
    ) {
        stringResource(R.string.home_headline_camera_one)
    } else {
        stringResource(R.string.home_headline_cameras, count)
    }

@Composable
private fun ModeCard(mode: PlannedMode) {
    SideriaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(mode.nameRes),
                style = Siderea.text.readout,
                color = Siderea.palette.onSurfaceMuted,
            )
            StatusPill(text = mode.release)
        }
        Spacer(Modifier.height(SideriaSpacing.xs))
        Text(
            text = stringResource(R.string.home_mode_coming, mode.release),
            style = Siderea.text.readoutSmall,
            color = Siderea.palette.onSurfaceMuted,
            modifier = Modifier.padding(end = SideriaSpacing.sm),
        )
    }
}
