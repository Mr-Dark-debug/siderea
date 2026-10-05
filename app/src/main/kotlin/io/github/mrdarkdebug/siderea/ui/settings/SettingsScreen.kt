package io.github.mrdarkdebug.siderea.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.R
import io.github.mrdarkdebug.siderea.core.ui.components.KeyValueRow
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.SectionLabel
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaDimens
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import io.github.mrdarkdebug.siderea.update.UpdateSettings
import io.github.mrdarkdebug.siderea.update.UpdateViewModel

private const val REPO_URL = "https://github.com/Mr-Dark-debug/siderea"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenInspector: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenGallery: () -> Unit,
    updates: UpdateViewModel,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember(context) { appVersionLabel(context) }
    val linkFailed = stringResource(R.string.settings_open_link_failed)
    val nav = WindowInsets.navigationBars.asPaddingValues()

    Column(Modifier.fillMaxSize()) {
        SideriaTopBar(
            title = stringResource(R.string.settings_title),
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = stringResource(R.string.action_back),
            onNavigationClick = onBack,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    start = SideriaSpacing.gutter,
                    end = SideriaSpacing.gutter,
                    bottom = nav.calculateBottomPadding() + SideriaSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
        ) {
            item { SectionLabel(stringResource(R.string.settings_display)) }
            item {
                SideriaCard {
                    SwitchRow(
                        title = stringResource(R.string.settings_red_mode),
                        hint = stringResource(R.string.settings_red_mode_hint),
                        checked = settings.redMode,
                        onCheckedChange = viewModel::setRedMode,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_haptics),
                        hint = stringResource(R.string.settings_haptics_hint),
                        checked = settings.hapticsEnabled,
                        onCheckedChange = viewModel::setHapticsEnabled,
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.settings_tools)) }
            item {
                SideriaCard(onClick = onOpenGallery) {
                    Text("Gallery", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("Photos, dates & details", color = Siderea.palette.onSurfaceMuted)
                }
            }
            item {
                SideriaCard(onClick = onOpenSessions) {
                    Text(
                        stringResource(R.string.settings_sessions),
                        style = Siderea.text.readout,
                        color = Siderea.palette.onBackground,
                    )
                    Text(
                        stringResource(R.string.settings_sessions_hint),
                        style = Siderea.text.readoutSmall,
                        color = Siderea.palette.onSurfaceMuted,
                    )
                }
            }
            item {
                SideriaCard(onClick = onOpenInspector) {
                    Text(
                        stringResource(R.string.settings_open_inspector),
                        style = Siderea.text.readout,
                        color = Siderea.palette.onBackground,
                    )
                    Text(
                        stringResource(R.string.settings_open_inspector_hint),
                        style = Siderea.text.readoutSmall,
                        color = Siderea.palette.onSurfaceMuted,
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.settings_data)) }
            item {
                SideriaCard {
                    Text(
                        stringResource(R.string.settings_reset_hint),
                        style = Siderea.text.readoutSmall,
                        color = Siderea.palette.onSurfaceMuted,
                    )
                    Spacer(Modifier.height(SideriaSpacing.md))
                    PillButton(
                        text = stringResource(R.string.settings_reset),
                        onClick = viewModel::reset,
                        style = PillStyle.Outlined,
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.settings_about)) }
            item { SideriaCard { UpdateSettings(updates) } }
            item {
                SideriaCard {
                    KeyValueRow(stringResource(R.string.settings_version), version)
                    KeyValueRow(
                        stringResource(R.string.settings_license),
                        stringResource(R.string.settings_license_value),
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = SideriaDimens.touchTargetMin)
                                .clickable(role = Role.Button) { openLink(context, REPO_URL, linkFailed) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KeyValueRow(
                            stringResource(R.string.settings_source),
                            stringResource(R.string.settings_source_value),
                            valueHighlighted = true,
                        )
                    }
                    Spacer(Modifier.height(SideriaSpacing.sm))
                    PillButton(
                        text = stringResource(R.string.settings_licenses),
                        onClick = onOpenLicenses,
                        style = PillStyle.Subtle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** A full-width toggle row. The whole row is the 56dp target, not just the switch. */
@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = Siderea.palette
    val haptics = Siderea.haptics
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SideriaDimens.touchTarget)
                .toggleable(value = checked, role = Role.Switch) {
                    haptics.toggle(it)
                    onCheckedChange(it)
                }.padding(vertical = SideriaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.lg),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Siderea.text.readout, color = palette.onBackground)
            Text(hint, style = Siderea.text.readoutSmall, color = palette.onSurfaceMuted)
        }
        // The Row owns the click; the Switch is a passive indicator.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = palette.onAccent,
                    checkedTrackColor = palette.accent,
                    uncheckedThumbColor = palette.onSurfaceMuted,
                    uncheckedTrackColor = palette.surfaceRaised,
                    uncheckedBorderColor = palette.onSurfaceMuted,
                ),
        )
    }
}

private fun appVersionLabel(context: Context): String {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    return "${info?.versionName ?: "unknown"} (${info?.longVersionCode ?: 0})"
}

private fun openLink(
    context: Context,
    url: String,
    failureMessage: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
    }
}
