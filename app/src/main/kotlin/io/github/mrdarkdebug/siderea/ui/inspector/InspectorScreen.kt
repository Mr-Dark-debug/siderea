package io.github.mrdarkdebug.siderea.ui.inspector

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.TextUnit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.R
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraInfo
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraSections
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityReport
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilitySummary
import io.github.mrdarkdebug.siderea.core.camera.capability.FeatureSupport
import io.github.mrdarkdebug.siderea.core.camera.capability.SupportStatus
import io.github.mrdarkdebug.siderea.core.ui.components.CapabilityChip
import io.github.mrdarkdebug.siderea.core.ui.components.IconTarget
import io.github.mrdarkdebug.siderea.core.ui.components.KeyValueRow
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.SectionLabel
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaDimens
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing

@Composable
fun InspectorScreen(
    onBack: () -> Unit,
    viewModel: InspectorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InspectorEffects(viewModel)
    InspectorContent(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

/** Performs the one-shot effects the ViewModel emits: clipboard and share sheet. */
@Composable
private fun InspectorEffects(viewModel: InspectorViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val copiedMessage = stringResource(R.string.inspector_copied)
    val shareFailedMessage = stringResource(R.string.inspector_share_failed)
    val chooserTitle = stringResource(R.string.inspector_share_chooser)
    val subject = stringResource(R.string.inspector_share_subject)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is InspectorEffect.CopyToClipboard -> {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Siderea capability report", effect.json)))
                    // Android 13+ shows its own "copied" confirmation; earlier versions need one from us.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                is InspectorEffect.Share -> {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, effect.uri)
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            clipData = ClipData.newRawUri(null, effect.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(send, chooserTitle))
                }

                InspectorEffect.ShareFailed -> {
                    Toast.makeText(context, shareFailedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun InspectorContent(
    state: InspectorState,
    onIntent: (InspectorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = WindowInsets.navigationBars.asPaddingValues()
    Column(modifier = modifier.fillMaxSize()) {
        SideriaTopBar(
            title = stringResource(R.string.inspector_title),
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = stringResource(R.string.action_back),
            onNavigationClick = onBack,
            actions = {
                IconTarget(
                    icon = Icons.Default.Refresh,
                    description = stringResource(R.string.inspector_refresh),
                    onClick = { onIntent(InspectorIntent.Refresh) },
                )
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    start = SideriaSpacing.gutter,
                    end = SideriaSpacing.gutter,
                    top = SideriaSpacing.sm,
                    bottom = nav.calculateBottomPadding() + SideriaSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(SideriaSpacing.md),
        ) {
            val report = state.report
            when {
                report != null -> {
                    reportItems(report, state, onIntent)
                }

                state.error != null -> {
                    item { ErrorCard(state.error, onRetry = { onIntent(InspectorIntent.Refresh) }) }
                }

                else -> {
                    item {
                        Text(
                            text = stringResource(R.string.inspector_loading),
                            style = Siderea.text.readoutSmall,
                            color = Siderea.palette.onSurfaceMuted,
                            modifier = Modifier.padding(vertical = SideriaSpacing.xl),
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.reportItems(
    report: CapabilityReport,
    state: InspectorState,
    onIntent: (InspectorIntent) -> Unit,
) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.md), modifier = Modifier.fillMaxWidth()) {
            PillButton(
                text = stringResource(R.string.inspector_copy_json),
                onClick = { onIntent(InspectorIntent.CopyJson) },
                style = PillStyle.Outlined,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = SideriaSpacing.md),
            )
            PillButton(
                text = stringResource(R.string.inspector_share),
                onClick = { onIntent(InspectorIntent.ShareReport) },
                style = PillStyle.Filled,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = SideriaSpacing.md),
            )
        }
    }
    item { DeviceCard(report) }
    if (report.readErrors.isNotEmpty()) item { ReadErrorsCard(report.readErrors) }
    item { SectionLabel(stringResource(R.string.inspector_cameras) + " · ${report.cameras.size}") }
    items(report.cameras, key = { it.id }) { camera ->
        CameraCard(
            camera = camera,
            expanded = camera.id in state.expanded,
            onToggle = { onIntent(InspectorIntent.ToggleCamera(camera.id)) },
        )
    }
}

@Composable
private fun DeviceCard(report: CapabilityReport) {
    val summary = CapabilitySummary.from(report)
    SideriaCard {
        Text(
            text = stringResource(R.string.inspector_device).uppercase(),
            style = Siderea.text.caption,
            color = Siderea.palette.onSurfaceMuted,
        )
        Spacer(Modifier.height(SideriaSpacing.xs))
        Text(summary.deviceName, style = Siderea.text.readout, color = Siderea.palette.onBackground)
        Spacer(Modifier.height(SideriaSpacing.sm))
        KeyValueRow("Android", "${report.device.androidRelease} (API ${report.device.sdkInt})")
        KeyValueRow("Security patch", report.device.securityPatch.ifBlank { "unknown" })
        KeyValueRow("Hardware", report.device.hardware)
        KeyValueRow("Siderea", "${report.app.versionName} (${report.app.versionCode})")
        KeyValueRow("Cameras listed", report.cameraIdsListed.joinToString(", ").ifEmpty { "none" })
        if (report.concurrentCameraSets.isNotEmpty()) {
            KeyValueRow(
                "Can run together",
                report.concurrentCameraSets.joinToString("  ") { "[" + it.joinToString("+") + "]" },
            )
        }
        KeyValueRow("Report generated", report.generatedAt)
    }
}

@Composable
private fun ReadErrorsCard(errors: List<String>) {
    SideriaCard {
        Text(
            text = stringResource(R.string.inspector_read_errors).uppercase(),
            style = Siderea.text.caption,
            color = Siderea.palette.danger,
        )
        Spacer(Modifier.height(SideriaSpacing.xs))
        errors.forEach { error ->
            Text(
                text = error,
                style = Siderea.text.readoutSmall,
                color = Siderea.palette.onBackground,
                modifier = Modifier.padding(vertical = SideriaSpacing.xxs),
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    SideriaCard {
        Text(
            text = stringResource(R.string.home_failed_title),
            style = Siderea.text.readout,
            color = Siderea.palette.danger,
        )
        Spacer(Modifier.height(SideriaSpacing.xs))
        Text(message, style = Siderea.text.readoutSmall, color = Siderea.palette.onSurfaceMuted)
        Spacer(Modifier.height(SideriaSpacing.md))
        PillButton(text = stringResource(R.string.action_retry), onClick = onRetry, style = PillStyle.Outlined)
    }
}

@Composable
private fun CameraCard(
    camera: CameraInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val title = CameraSections.title(camera)
    val expandedText = stringResource(R.string.inspector_collapse, title)
    val collapsedText = stringResource(R.string.inspector_expand, title)
    SideriaCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SideriaDimens.touchTargetMin)
                    .semantics {
                        heading()
                        stateDescription = if (expanded) expandedText else collapsedText
                    }.clickable(role = Role.Button, onClick = onToggle),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = Siderea.text.readoutSmall,
                    color = Siderea.palette.onBackground,
                    modifier = Modifier.weight(1f),
                )
                CapabilityChip(text = if (expanded) "−" else "+", on = false)
            }
            Spacer(Modifier.height(SideriaSpacing.xs))
            Text(
                text = CameraSections.summaryLine(camera),
                style = Siderea.text.caption,
                color = Siderea.palette.onSurfaceMuted,
            )
        }
        if (expanded) CameraDetails(camera)
    }
}

@Composable
private fun CameraDetails(camera: CameraInfo) {
    if (camera.features.isNotEmpty()) {
        SectionLabel(stringResource(R.string.inspector_features))
        camera.features.forEach { FeatureRow(it) }
    }
    CameraSections.of(camera).forEach { section ->
        SectionLabel(section.title)
        section.rows.forEach { row -> KeyValueRow(label = row.label, value = row.value) }
    }
}

@Composable
private fun FeatureRow(feature: FeatureSupport) {
    val label =
        when (feature.status) {
            SupportStatus.SUPPORTED -> stringResource(R.string.inspector_status_supported)
            SupportStatus.LIMITED -> stringResource(R.string.inspector_status_limited)
            SupportStatus.UNSUPPORTED -> stringResource(R.string.inspector_status_unsupported)
        }
    Column(Modifier.fillMaxWidth().padding(vertical = SideriaSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CapabilityChip(
                text = label,
                on = feature.status == SupportStatus.SUPPORTED,
                partial = feature.status == SupportStatus.LIMITED,
            )
            Text(
                text = feature.title,
                style = Siderea.text.readoutSmall,
                color = Siderea.palette.onBackground,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = feature.detail,
            style = Siderea.text.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = Siderea.palette.onSurfaceMuted,
            modifier = Modifier.padding(top = SideriaSpacing.xxs),
        )
    }
}
