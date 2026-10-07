package io.github.mrdarkdebug.siderea.update

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UpdatePrompt(
    viewModel: UpdateViewModel,
    canPrompt: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.foreground()
        onPauseOrDispose {}
    }
    if (canPrompt && state.showPrompt && state.readyVersion != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update ready") },
            text = {
                Text(
                    "Siderea ${state.readyVersion} is downloaded and verified. " +
                        "Android will ask you to approve installation.",
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.install(context) }) { Text("Install") } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Later") } },
            containerColor = Siderea.palette.surface,
        )
    }
}

@Composable
fun UpdatesScreen(
    viewModel: UpdateViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val installedVersion =
        remember(context) {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
        }
    val title =
        when {
            state.progress != null -> "Downloading update"
            state.checking -> "Checking for updates"
            state.readyVersion != null -> "Ready to install"
            state.problem -> "Try again"
            state.candidate != null -> "A new version is available"
            state.message == "You're up to date" -> "You're up to date"
            else -> "Keep Siderea up to date"
        }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SideriaTopBar(
            "Updates",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = "Back to settings",
            onNavigationClick = onBack,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SideriaCard {
                Icon(Icons.Default.SystemUpdate, null, tint = Siderea.palette.accent)
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Siderea.palette.onBackground)
                Text(
                    "Installed $installedVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Siderea.palette.onSurfaceMuted,
                )
                state.readyVersion?.let {
                    Text(
                        "Version $it · Verified and downloaded",
                        color = Siderea.palette.accent,
                    )
                }
                state.progress?.let { progress ->
                    Text("${(progress * 100).toInt()}%", color = Siderea.palette.accent)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Siderea.palette.accent,
                    )
                }
                state.message
                    ?.takeIf {
                        it != "You're up to date"
                    }?.let {
                        Text(
                            it,
                            color = if (state.problem) Siderea.palette.danger else Siderea.palette.onSurfaceMuted,
                        )
                    }
                when {
                    state.progress != null -> {
                        PillButton("Cancel download", viewModel::cancel, modifier = Modifier.fillMaxWidth())
                    }

                    state.readyVersion != null -> {
                        PillButton("Install ${state.readyVersion}", {
                            viewModel.install(context)
                        }, style = PillStyle.Filled, modifier = Modifier.fillMaxWidth())
                    }

                    state.candidate != null && !state.checking -> {
                        PillButton(
                            "Download ${state.candidate?.version}",
                            viewModel::download,
                            style = PillStyle.Filled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    else -> {
                        PillButton(
                            if (state.checking) "Checking…" else "Check for updates",
                            viewModel::check,
                            enabled = !state.checking,
                            style = PillStyle.Filled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (state.readyVersion !=
                    null
                ) {
                    Text(
                        "Android will ask you to approve installation.",
                        style = Siderea.text.caption,
                        color = Siderea.palette.onSurfaceMuted,
                    )
                }
                if (state.lastCheck >
                    0
                ) {
                    Text(
                        "Last checked ${Instant.ofEpochMilli(
                            state.lastCheck,
                        ).atZone(ZoneId.systemDefault()).format(CHECK_DATE)}",
                        style = Siderea.text.caption,
                        color = Siderea.palette.onSurfaceMuted,
                    )
                }
            }
            SideriaCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            state.automatic,
                            role = Role.Switch,
                            onValueChange = viewModel::setAutomatic,
                        ).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic updates", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Check on launch · Download on Wi-Fi",
                            style = Siderea.text.caption,
                            color = Siderea.palette.onSurfaceMuted,
                        )
                    }
                    Switch(state.automatic, onCheckedChange = null)
                }
            }
            PillButton("Release notes on GitHub", {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                } catch (
                    _: android.content.ActivityNotFoundException,
                ) {
                    android.widget.Toast
                        .makeText(
                            context,
                            "Install a browser to open release notes.",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            }, style = PillStyle.Subtle, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val CHECK_DATE = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.getDefault())
private const val RELEASES_URL = "https://github.com/Mr-Dark-debug/siderea/releases/latest"
