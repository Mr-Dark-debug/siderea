package io.github.mrdarkdebug.siderea.ui.sessions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraFormat
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.session.SessionSummary
import io.github.mrdarkdebug.siderea.core.capture.timelapse.IntervalMath
import io.github.mrdarkdebug.siderea.core.export.VideoSpec
import io.github.mrdarkdebug.siderea.core.ui.components.CapabilityChip
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.KeyValueRow
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.SectionLabel
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(ms: Long) = stamp.format(Instant.ofEpochMilli(ms))

private const val GIGA = 1_000_000_000L
private const val MEGA = 1_000_000L

private fun size(bytes: Long) =
    if (bytes >= GIGA) "%.1f GB".format(java.util.Locale.ROOT, bytes / GIGA.toDouble()) else "${bytes / MEGA} MB"

private fun statusLabel(status: SessionStatus) =
    when (status) {
        SessionStatus.RUNNING -> "RUNNING"
        SessionStatus.COMPLETED -> "DONE"
        SessionStatus.STOPPED -> "STOPPED"
        SessionStatus.INTERRUPTED -> "INTERRUPTED"
        SessionStatus.FAILED -> "ENDED EARLY"
    }

@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val nav = WindowInsets.navigationBars.asPaddingValues()
    Column(Modifier.fillMaxSize()) {
        SideriaTopBar(
            title = "Sessions",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = "Back",
            onNavigationClick = onBack,
        )
        if (sessions.isEmpty()) {
            Text(
                "No sessions yet. Choose TIMELAPSE on the camera, set an interval and press the shutter.",
                style = Siderea.text.readoutSmall,
                color = Siderea.palette.onSurfaceMuted,
                modifier = Modifier.padding(SideriaSpacing.gutter),
            )
        }
        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = SideriaSpacing.gutter,
                    end = SideriaSpacing.gutter,
                    bottom = nav.calculateBottomPadding() + SideriaSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(SideriaSpacing.md),
        ) {
            items(sessions, key = { it.id }) { summary -> SessionRow(summary, viewModel) { onOpen(summary.id) } }
        }
    }
}

@Composable
private fun SessionRow(
    summary: SessionSummary,
    viewModel: SessionsViewModel,
    onClick: () -> Unit,
) {
    val thumb by produceState<Bitmap?>(null, summary.id, summary.frameCount) {
        value =
            viewModel.thumbnail(summary)?.let { f -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path) } }
    }
    SideriaCard(onClick = onClick) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(72.dp).clip(SideriaShapes.small).background(Siderea.palette.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                thumb?.let {
                    Image(
                        it.asImageBitmap(),
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SideriaSpacing.xxs)) {
                Text(summary.name, style = Siderea.text.readout, color = Siderea.palette.onBackground, maxLines = 1)
                Text(
                    buildString {
                        append(summary.kind.title)
                        append(" · ${summary.frameCount}")
                        summary.plannedFrames?.let { append(" of $it") }
                        append(" frames")
                    },
                    style = Siderea.text.readoutSmall,
                    color = Siderea.palette.onSurfaceMuted,
                )
                Text(
                    "${formatTime(
                        summary.createdAtEpochMs,
                    )} · ${IntervalMath.formatSeconds(summary.spanMs)} · ${size(summary.sizeBytes)}",
                    style = Siderea.text.caption,
                    color = Siderea.palette.onSurfaceMuted,
                )
            }
            CapabilityChip(
                statusLabel(summary.status),
                on = summary.status == SessionStatus.COMPLETED,
                partial =
                    summary.status != SessionStatus.COMPLETED,
            )
        }
    }
}

@Composable
fun SessionDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenCamera: () -> Unit = {},
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var videoDialog by remember { mutableStateOf(false) }
    var tiffFrame by remember { mutableStateOf<String?>(null) }
    var astroDialog by remember { mutableStateOf(false) }
    var darkDialog by remember { mutableStateOf(false) }
    val nav = WindowInsets.navigationBars.asPaddingValues()
    val d = detail?.takeIf { it.manifest.id == id }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SideriaTopBar(
                title = d?.manifest?.name ?: "Session",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationDescription = "Back",
                onNavigationClick = onBack,
            )
            if (d == null) {
                Text(
                    "Loading…",
                    style = Siderea.text.readoutSmall,
                    color = Siderea.palette.onSurfaceMuted,
                    modifier = Modifier.padding(SideriaSpacing.gutter),
                )
            } else {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            start = SideriaSpacing.gutter,
                            end = SideriaSpacing.gutter,
                            bottom = nav.calculateBottomPadding() + SideriaSpacing.xl,
                        ),
                    verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
                ) {
                    item { PreviewStrip(d.previews) { tiffFrame = it.nameWithoutExtension } }
                    item { Facts(d) }
                    if (d.manifest.events.isNotEmpty()) {
                        item { SectionLabel("Events") }
                        items(d.manifest.events) { event ->
                            Text(
                                "${DateTimeFormatter.ofPattern(
                                    "HH:mm:ss",
                                ).withZone(
                                    ZoneId.systemDefault(),
                                ).format(Instant.ofEpochMilli(event.atEpochMs))}  ${event.text}",
                                style = Siderea.text.readoutSmall,
                                color = Siderea.palette.onSurfaceMuted,
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(SideriaSpacing.sm))
                        ExportSection(d, exportState, viewModel) { videoDialog = true }
                    }
                    if (d.jpegFrames >= 2) {
                        item {
                            AstroSection(d, exportState, { astroDialog = true }, { darkDialog = true })
                        }
                    }
                    item {
                        Spacer(Modifier.height(SideriaSpacing.md))
                        Text(
                            "Frames and session.json are in app storage:\n${d.folder.path}\n" +
                                "Tap a preview above to save that frame as a TIFF.",
                            style = Siderea.text.caption,
                            color = Siderea.palette.onSurfaceMuted,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
                            PillButton(
                                "Rename",
                                { renaming = true },
                                style = PillStyle.Subtle,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                "Delete",
                                { confirmDelete = true },
                                style = PillStyle.Outlined,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        if (astroDialog && d != null) {
            if (d.manifest.timelapse?.skyPreset == io.github.mrdarkdebug.siderea.core.capture.session.SkyPreset.MOON) {
                MoonDialog(d, { astroDialog = false }) {
                    astroDialog = false
                    viewModel.exportBulb(id, io.github.mrdarkdebug.siderea.core.processing.BulbMode.AVERAGE)
                }
            } else if (d.manifest.kind == SessionKind.LONG_EXPOSURE) {
                BulbDialog(d, { astroDialog = false }) { mode ->
                    astroDialog = false
                    viewModel.exportBulb(id, mode)
                }
            } else {
                AstroDialog(d, { astroDialog = false }) { mode, darks, brighten ->
                    astroDialog = false
                    viewModel.exportAstro(id, mode, darks, brighten)
                }
            }
        }
        if (darkDialog && d != null) {
            DarkFramesDialog(d, viewModel.sessionRunning, { darkDialog = false }) { count ->
                darkDialog = false
                if (viewModel.captureDarks(id, count, d.orientation)) onOpenCamera()
            }
        }
        if (videoDialog && d != null) {
            VideoDialog(d, viewModel, d.manifest.timelapse?.outputFps ?: VideoSpec.DEFAULT_FPS, {
                videoDialog = false
            }) { spec, skip ->
                videoDialog = false
                viewModel.exportVideo(id, spec, skip)
            }
        }
        tiffFrame?.let { frame ->
            ConfirmDialog(
                title = "Save $frame as TIFF?",
                body = "Makes an uncompressed 8-bit TIFF from this frame's JPEG, in the session's exports folder.",
                confirm = "Save TIFF",
                onDismiss = { tiffFrame = null },
            ) {
                tiffFrame = null
                viewModel.exportTiff(id, frame)
            }
        }
        if (renaming && d != null) {
            RenameDialog(d.manifest.name, { renaming = false }) { name ->
                renaming = false
                viewModel.rename(id, name)
            }
        }
        if (confirmDelete && d != null) {
            ConfirmDialog(
                title = "Delete ${d.manifest.name}?",
                body = "This permanently deletes ${d.usableFrames} frames (${size(
                    d.summary.sizeBytes,
                )}). It can't be undone.",
                confirm = "Delete",
                onDismiss = { confirmDelete = false },
            ) {
                confirmDelete = false
                viewModel.delete(id, onBack)
            }
        }
    }
}

@Composable
private fun PreviewStrip(
    previews: List<File>,
    onClick: (File) -> Unit,
) {
    if (previews.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
        previews.forEach { file ->
            val bitmap by produceState<Bitmap?>(null, file) {
                value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }
            }
            Box(
                Modifier
                    .weight(
                        1f,
                    ).aspectRatio(PREVIEW_ASPECT)
                    .clip(SideriaShapes.small)
                    .background(Siderea.palette.surfaceRaised)
                    .clickable(onClickLabel = "Save this frame as TIFF") { onClick(file) },
            ) {
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        "Frame preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private const val PREVIEW_ASPECT = 0.75f

private fun exposureNote(config: io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig): String =
    when {
        config.rampExposure -> ", exposure ramped"
        config.lockExposure -> ", exposure locked"
        else -> ", exposure per frame"
    }

@Composable
private fun Facts(d: SessionDetail) {
    val m = d.manifest
    SideriaCard {
        KeyValueRow("Status", statusLabel(m.status))
        KeyValueRow("Started", formatTime(m.createdAtEpochMs))
        m.finishedAtEpochMs?.let { KeyValueRow("Ended", formatTime(it)) }
        KeyValueRow("Frames", "${d.usableFrames}" + (m.timelapse?.plannedFrames?.let { " of $it" } ?: ""))
        if (d.failedFrames > 0) KeyValueRow("Failed frames", d.failedFrames.toString())
        if (d.flaggedFrames > 0) KeyValueRow("Flagged (phone moved)", d.flaggedFrames.toString())
        KeyValueRow("Size on disk", size(d.summary.sizeBytes))
        m.timelapse?.let {
            KeyValueRow(
                "Interval",
                "${IntervalMath.formatSeconds(
                    it.intervalMs,
                )}${exposureNote(it)}",
            )
        }
        KeyValueRow(
            "Lens",
            "${m.camera.zoomLabel} ${m.camera.facing.lowercase()} (camera ${m.camera.openId}${m.camera.physicalId?.let {
                "/$it"
            } ?: ""})",
        )
        KeyValueRow("Shutter", CameraFormat.exposure(m.requested.settings.shutterNs))
        KeyValueRow(
            "ISO",
            m.requested.settings.iso
                .toString(),
        )
        KeyValueRow("Format", m.requested.settings.format.name)
        KeyValueRow("Phone", "${m.device.manufacturer} ${m.device.model}, Android ${m.device.androidRelease}")
        KeyValueRow("Siderea", m.app.versionName)
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Siderea.palette.background.copy(alpha = SCRIM),
            ).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SideriaCard(Modifier.padding(SideriaSpacing.lg).clickable(enabled = false) {}) {
            Text("RENAME", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
            Spacer(Modifier.height(SideriaSpacing.sm))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SideriaSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
                PillButton("Cancel", onDismiss, style = PillStyle.Subtle, modifier = Modifier.weight(1f))
                PillButton("Save", { onSave(text) }, style = PillStyle.Filled, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Siderea.palette.background.copy(alpha = SCRIM),
            ).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SideriaCard(Modifier.padding(SideriaSpacing.lg).clickable(enabled = false) {}) {
            Text(title, style = Siderea.text.readout, color = Siderea.palette.onBackground)
            Spacer(Modifier.height(SideriaSpacing.sm))
            Text(body, style = Siderea.text.readoutSmall, color = Siderea.palette.onSurfaceMuted)
            Spacer(Modifier.height(SideriaSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
                PillButton("Keep it", onDismiss, style = PillStyle.Subtle, modifier = Modifier.weight(1f))
                PillButton(confirm, onConfirm, style = PillStyle.Filled, modifier = Modifier.weight(1f))
            }
        }
    }
}

private const val SCRIM = 0.85f
