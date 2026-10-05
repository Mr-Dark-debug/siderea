package io.github.mrdarkdebug.siderea.ui.sessions

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.export.CropAspect
import io.github.mrdarkdebug.siderea.core.export.DeflickerLevel
import io.github.mrdarkdebug.siderea.core.export.OutputSize
import io.github.mrdarkdebug.siderea.core.export.VideoQuality
import io.github.mrdarkdebug.siderea.core.export.VideoSpec
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.SectionLabel
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import io.github.mrdarkdebug.siderea.export.ExportState

private const val SCRIM = 0.82f
private const val SHEET_MAX_DP = 640
private const val BYTES_PER_MB = 1_000_000.0

private fun sizeText(bytes: Long): String =
    if (bytes < BYTES_PER_MB) "under 1 MB" else "about ${"%.0f".format(bytes / BYTES_PER_MB)} MB"

/** Export actions for one session: what you can make, progress, and what to do with the result. */
@Composable
internal fun ExportSection(
    detail: SessionDetail,
    state: ExportState,
    viewModel: SessionsViewModel,
    onVideo: () -> Unit,
) {
    val id = detail.manifest.id
    val mine =
        when (state) {
            is ExportState.Working -> state.sessionId == id
            is ExportState.Done -> state.sessionId == id
            is ExportState.Failed -> state.sessionId == id
            ExportState.Idle -> false
        }
    Column(verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
        SectionLabel("Export")
        if (mine) {
            when (state) {
                is ExportState.Working -> WorkingCard(state, viewModel::cancelExport)
                is ExportState.Done -> DoneCard(state, viewModel)
                is ExportState.Failed -> FailedCard(state.message, viewModel::dismissExport)
                ExportState.Idle -> Unit
            }
        } else {
            val busy = state is ExportState.Working
            Text(
                if (detail.jpegFrames ==
                    0
                ) {
                    "No JPEG frames in this session, so there is nothing to turn into a video."
                } else {
                    "Frames stay untouched in the session folder. Exports are separate files."
                },
                style = Siderea.text.caption,
                color = Siderea.palette.onSurfaceMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
                PillButton(
                    "Make video",
                    onVideo,
                    style = PillStyle.Filled,
                    enabled = detail.jpegFrames > 0 && !busy,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    "ZIP frames",
                    { viewModel.exportZip(id, includeRaw = detail.hasRaw) },
                    style = PillStyle.Subtle,
                    enabled = detail.usableFrames > 0 && !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            if (busy) {
                Text(
                    "Another export is running. Wait for it to finish.",
                    style = Siderea.text.caption,
                    color = Siderea.palette.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun WorkingCard(
    state: ExportState.Working,
    onCancel: () -> Unit,
) {
    SideriaCard {
        Text("MAKING ${state.title.uppercase()}", style = Siderea.text.caption, color = Siderea.palette.accent)
        Spacer(Modifier.height(SideriaSpacing.xs))
        Text(
            if (state.total > 0) "${state.phase}: ${state.done} of ${state.total}" else state.phase,
            style = Siderea.text.readoutSmall,
            color = Siderea.palette.onBackground,
        )
        Spacer(Modifier.height(SideriaSpacing.sm))
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
                color = Siderea.palette.accent,
                trackColor = Siderea.palette.surfaceRaised,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Siderea.palette.accent,
                trackColor = Siderea.palette.surfaceRaised,
            )
        }
        Spacer(Modifier.height(SideriaSpacing.md))
        PillButton("Cancel", onCancel, style = PillStyle.Subtle, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DoneCard(
    done: ExportState.Done,
    viewModel: SessionsViewModel,
) {
    val context = LocalContext.current
    var message by remember(done) { mutableStateOf<String?>(null) }
    SideriaCard {
        Text("${done.title.uppercase()} READY", style = Siderea.text.caption, color = Siderea.palette.accent)
        Spacer(Modifier.height(SideriaSpacing.xs))
        Text(done.file.name, style = Siderea.text.readoutSmall, color = Siderea.palette.onBackground)
        Text(done.detail, style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        message?.let {
            Spacer(Modifier.height(SideriaSpacing.xs))
            Text(it, style = Siderea.text.caption, color = Siderea.palette.accent)
        }
        Spacer(Modifier.height(SideriaSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
            PillButton(
                "Save to phone",
                {
                    message =
                        runCatching { viewModel.saveToGallery(done) }
                            .fold({ "Saved to ${done.kind.folder()}/Siderea." }, { "Couldn't save it: ${it.message}" })
                },
                style = PillStyle.Filled,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                "Share",
                {
                    runCatching {
                        val send =
                            Intent(Intent.ACTION_SEND)
                                .setType(done.mime)
                                .putExtra(Intent.EXTRA_STREAM, viewModel.shareUri(done))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Share ${done.file.name}"))
                    }.onFailure { message = "Couldn't open the share sheet." }
                },
                style = PillStyle.Subtle,
                modifier = Modifier.weight(1f),
            )
        }
        if (done.extra != null) {
            Spacer(Modifier.height(SideriaSpacing.sm))
            PillButton(
                "Save the TIFF too",
                {
                    message =
                        runCatching { viewModel.saveExtraToGallery(done) }
                            .fold(
                                { "TIFF saved to ${done.kind.folder()}/Siderea." },
                                { "Couldn't save it: ${it.message}" },
                            )
                },
                style = PillStyle.Subtle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(SideriaSpacing.sm))
        PillButton("Done", viewModel::dismissExport, style = PillStyle.Outlined, modifier = Modifier.fillMaxWidth())
    }
}

private fun io.github.mrdarkdebug.siderea.core.export.PublishKind.folder(): String =
    when (this) {
        io.github.mrdarkdebug.siderea.core.export.PublishKind.VIDEO -> "Movies"
        io.github.mrdarkdebug.siderea.core.export.PublishKind.IMAGE -> "Pictures"
        io.github.mrdarkdebug.siderea.core.export.PublishKind.DOWNLOAD -> "Download"
    }

@Composable
private fun FailedCard(
    message: String,
    onDismiss: () -> Unit,
) {
    SideriaCard {
        Text("EXPORT FAILED", style = Siderea.text.caption, color = Siderea.palette.accent)
        Spacer(Modifier.height(SideriaSpacing.xs))
        Text(message, style = Siderea.text.readoutSmall, color = Siderea.palette.onBackground)
        Spacer(Modifier.height(SideriaSpacing.md))
        PillButton("OK", onDismiss, style = PillStyle.Subtle, modifier = Modifier.fillMaxWidth())
    }
}

/** The video settings sheet, with a live estimate of the result. */
@Composable
internal fun VideoDialog(
    detail: SessionDetail,
    viewModel: SessionsViewModel,
    initialFps: Int,
    onDismiss: () -> Unit,
    onStart: (VideoSpec, Boolean) -> Unit,
) {
    val codecs = remember { viewModel.codecs() }
    var spec by remember {
        mutableStateOf(
            VideoSpec(codec = codecs.firstOrNull() ?: VideoSpec().codec, fps = initialFps),
        )
    }
    var skipMoved by remember { mutableStateOf(false) }
    val estimate by produceState<VideoEstimate?>(null, spec, skipMoved) {
        value =
            viewModel.estimate(detail, spec, skipMoved)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Siderea.palette.background.copy(alpha = SCRIM))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SideriaCard(Modifier.padding(SideriaSpacing.lg).clickable(enabled = false) {}) {
            Column(Modifier.heightIn(max = SHEET_MAX_DP.dp).verticalScroll(rememberScrollState())) {
                Text("MAKE A VIDEO", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
                if (codecs.isEmpty()) {
                    Spacer(Modifier.height(SideriaSpacing.sm))
                    Text(
                        "This phone has no video encoder Siderea can use. You can still zip the frames.",
                        style = Siderea.text.readoutSmall,
                        color = Siderea.palette.onBackground,
                    )
                } else {
                    Option("Format", codecs.map { it.label }, codecs.indexOf(spec.codec)) {
                        spec =
                            spec.copy(codec = codecs[it])
                    }
                    Option(
                        "Frame rate",
                        VideoSpec.FPS_CHOICES.map { "$it fps" },
                        VideoSpec.FPS_CHOICES.indexOf(spec.fps),
                    ) {
                        spec = spec.copy(fps = VideoSpec.FPS_CHOICES[it])
                    }
                    Option("Size", OutputSize.entries.map { it.label }, spec.size.ordinal) {
                        spec =
                            spec.copy(size = OutputSize.entries[it])
                    }
                    Option("Crop", CropAspect.entries.map { it.label }, spec.crop.ordinal) {
                        spec =
                            spec.copy(crop = CropAspect.entries[it])
                    }
                    Option("Deflicker", DeflickerLevel.entries.map { it.label }, spec.deflicker.ordinal) {
                        spec = spec.copy(deflicker = DeflickerLevel.entries[it])
                    }
                    Option("Quality", VideoQuality.entries.map { it.label }, spec.quality.ordinal) {
                        spec = spec.copy(quality = VideoQuality.entries[it])
                    }
                    if (detail.movedFrames > 0) {
                        Spacer(Modifier.height(SideriaSpacing.sm))
                        ChipButton(
                            "LEAVE OUT ${detail.movedFrames} MOVED FRAMES",
                            { skipMoved = !skipMoved },
                            selected = skipMoved,
                        )
                    }
                    Spacer(Modifier.height(SideriaSpacing.md))
                    Text(
                        estimate?.let {
                            "${it.frames} frames, ${it.size.width}x${it.size.height}, ${"%.1f".format(
                                it.seconds,
                            )} s, " +
                                sizeText(it.bytes)
                        } ?: "Working out the size…",
                        style = Siderea.text.readoutSmall,
                        color = Siderea.palette.onBackground,
                    )
                    Text(
                        "The phone's encoder may make it smaller if it can't do this size.",
                        style = Siderea.text.caption,
                        color = Siderea.palette.onSurfaceMuted,
                    )
                }
                Spacer(Modifier.height(SideriaSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
                    PillButton("Cancel", onDismiss, style = PillStyle.Subtle, modifier = Modifier.weight(1f))
                    PillButton(
                        "Start",
                        { onStart(spec, skipMoved) },
                        style = PillStyle.Filled,
                        enabled = codecs.isNotEmpty() && (estimate?.frames ?: 0) > 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Option(
    label: String,
    choices: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Spacer(Modifier.height(SideriaSpacing.sm))
    Text(label.uppercase(), style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
    ) {
        choices.forEachIndexed { index, text ->
            ChipButton(text.uppercase(), { onSelect(index) }, selected = index == selected)
        }
    }
}
