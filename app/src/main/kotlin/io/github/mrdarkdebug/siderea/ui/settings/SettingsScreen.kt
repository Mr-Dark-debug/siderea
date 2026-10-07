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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.R
import io.github.mrdarkdebug.siderea.core.ui.components.SectionLabel
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.update.UpdateViewModel
import io.github.mrdarkdebug.siderea.update.UpdatesScreen

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
    val updateState by updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version =
        remember(context) {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
        }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    if (showUpdates) {
        UpdatesScreen(updates) { showUpdates = false }
        return
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SideriaTopBar(
            stringResource(R.string.settings_title),
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = stringResource(R.string.action_back),
            onNavigationClick = onBack,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SideriaCard {
                    SettingsLink(
                        Icons.Default.SystemUpdate,
                        "Updates",
                        when {
                            updateState.readyVersion != null -> "${updateState.readyVersion} ready to install"
                            updateState.progress != null -> "Downloading ${(updateState.progress!! * 100).toInt()}%"
                            else -> "Siderea $version"
                        },
                    ) { showUpdates = true }
                }
            }
            item { SectionLabel("Display") }
            item {
                SideriaCard {
                    SwitchRow(
                        stringResource(R.string.settings_red_mode),
                        "Gentle on your eyes at night",
                        settings.redMode,
                        viewModel::setRedMode,
                    )
                    SwitchRow(
                        stringResource(R.string.settings_haptics),
                        "Feedback when you tap controls",
                        settings.hapticsEnabled,
                        viewModel::setHapticsEnabled,
                    )
                }
            }
            item { SectionLabel("Library") }
            item {
                SideriaCard {
                    SettingsLink(Icons.Default.PhotoLibrary, "Gallery", "Browse, edit & share", onOpenGallery)
                    SettingsLink(
                        Icons.Default.BurstMode,
                        stringResource(R.string.settings_sessions),
                        "Capture sessions & exports",
                        onOpenSessions,
                    )
                }
            }
            item { SectionLabel("App") }
            item {
                SideriaCard {
                    SettingsLink(
                        Icons.Default.Info,
                        "Capability Inspector",
                        "What your lenses support",
                        onOpenInspector,
                    )
                    SettingsLink(
                        Icons.Default.RestartAlt,
                        stringResource(R.string.settings_reset),
                        null,
                    ) { reset = true }
                    SettingsLink(Icons.Default.Code, "Open-source licenses", null, onOpenLicenses)
                    SettingsLink(Icons.Default.Code, "Source on GitHub", null) { openLink(context, REPO_URL) }
                }
            }
            item {
                Text(
                    "Siderea $version · Apache 2.0",
                    style = Siderea.text.caption,
                    color = Siderea.palette.onSurfaceMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
    if (reset) {
        AlertDialog(
            onDismissRequest = { reset = false },
            title = { Text("Reset settings?") },
            text = { Text("Restore camera and display defaults. Your photos and sessions are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    reset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } },
            containerColor = Siderea.palette.surface,
        )
    }
}

@Composable
private fun SettingsLink(
    icon: ImageVector,
    title: String,
    hint: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(
                min = 56.dp,
            ).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = Siderea.palette.accent)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Siderea.palette.onBackground)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Siderea.palette.onSurfaceMuted) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Siderea.palette.onSurfaceMuted)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = Siderea.haptics
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(checked, role = Role.Switch) {
                haptics.toggle(it)
                onCheckedChange(it)
            }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Siderea.palette.onBackground)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Siderea.palette.onSurfaceMuted)
        }
        Switch(checked, onCheckedChange = null)
    }
}

private fun openLink(
    context: Context,
    url: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (
        _: ActivityNotFoundException,
    ) {
        Toast.makeText(context, "Install a browser to open this link.", Toast.LENGTH_LONG).show()
    }
}

private const val REPO_URL = "https://github.com/Mr-Dark-debug/siderea"
