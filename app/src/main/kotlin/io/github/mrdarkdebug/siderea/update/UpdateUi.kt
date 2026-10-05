package io.github.mrdarkdebug.siderea.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea

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
fun UpdateSettings(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatic updates", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Check on launch · Download on Wi-Fi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Siderea.palette.onSurfaceMuted,
                )
            }
            Switch(state.automatic, viewModel::setAutomatic)
        }
        state.progress?.let { progress ->
            Text("Downloading ${(progress * 100).toInt()}%", color = Siderea.palette.accent)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Siderea.palette.accent,
            )
            PillButton("Cancel download", viewModel::cancel)
        }
        state.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = Siderea.palette.onSurfaceMuted,
            )
        }
        when {
            state.readyVersion != null -> {
                PillButton(
                    "Install ${state.readyVersion}",
                    { viewModel.install(context) },
                    style = PillStyle.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.candidate != null && state.progress == null -> {
                PillButton(
                    "Download ${state.candidate?.version}",
                    viewModel::download,
                    style = PillStyle.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        PillButton(
            if (state.checking) "Checking…" else "Check for updates",
            viewModel::check,
            enabled = !state.checking && state.progress == null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
