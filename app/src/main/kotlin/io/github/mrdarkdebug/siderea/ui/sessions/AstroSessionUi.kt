package io.github.mrdarkdebug.siderea.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SkyPreset
import io.github.mrdarkdebug.siderea.core.processing.BulbMode
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.SectionLabel
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import io.github.mrdarkdebug.siderea.export.AstroMode
import io.github.mrdarkdebug.siderea.export.ExportState

private const val SCRIM = 0.82f
private const val SHEET_MAX_DP = 640
private val darkCountChoices = listOf(5, 10, 20)

/** Star trails, stacking and dark frames for a session of sky pictures. */
@Composable
internal fun AstroSection(
    detail: SessionDetail,
    state: ExportState,
    onProcess: () -> Unit,
    onDarks: () -> Unit,
) {
    val busy = state is ExportState.Working
    val bulb = detail.manifest.kind == SessionKind.LONG_EXPOSURE
    val moon = detail.manifest.timelapse?.skyPreset == SkyPreset.MOON
    Column(verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
        SectionLabel(
            if (moon) {
                "Moon"
            } else if (bulb) {
                "Long exposure"
            } else {
                "Sky"
            },
        )
        Text(
            if (moon) {
                "Average the moon frames to reduce noise. Keep the phone on a tripod."
            } else if (bulb) {
                "Adds the frames together like one long exposure, keeps the brightest light, or averages them."
            } else {
                "Star trails, or an aligned stack that averages the noise away. " +
                    "Dark frames (taken with the lens covered) remove hot pixels and sensor glow."
            },
            style = Siderea.text.caption,
            color = Siderea.palette.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
            PillButton(
                if (moon) {
                    "Create moon photo"
                } else if (bulb) {
                    "Combine frames"
                } else {
                    "Trails / stack"
                },
                onProcess,
                style = PillStyle.Filled,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            if (!bulb && !moon) {
                PillButton(
                    if (detail.darkFrames > 0) "Darks (${detail.darkFrames})" else "Take darks",
                    onDarks,
                    style = PillStyle.Subtle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun MoonDialog(
    detail: SessionDetail,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
) {
    Sheet(onDismiss) {
        Text("MOON PHOTO", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        Text(
            "Average ${detail.jpegFrames} frames without star alignment. Saves a JPEG and TIFF.",
            style = Siderea.text.readoutSmall,
            color = Siderea.palette.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
            PillButton("Cancel", onDismiss, modifier = Modifier.weight(1f))
            PillButton("Start", onStart, style = PillStyle.Filled, modifier = Modifier.weight(1f))
        }
    }
}

/** Choose how the frames of a virtual bulb are combined. */
@Composable
internal fun BulbDialog(
    detail: SessionDetail,
    onDismiss: () -> Unit,
    onStart: (BulbMode) -> Unit,
) {
    var mode by remember { mutableStateOf(BulbMode.ADDITIVE) }
    Sheet(onDismiss) {
        Text("COMBINE FRAMES", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        Spacer(Modifier.height(SideriaSpacing.sm))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
        ) {
            BulbMode.entries.forEach { ChipButton(it.label.uppercase(), { mode = it }, selected = it == mode) }
        }
        Spacer(Modifier.height(SideriaSpacing.xs))
        Text(mode.description, style = Siderea.text.readoutSmall, color = Siderea.palette.onBackground)
        Spacer(Modifier.height(SideriaSpacing.sm))
        Text(
            "Uses ${detail.jpegFrames} frames. Saves a JPEG and a TIFF.",
            style = Siderea.text.caption,
            color = Siderea.palette.onSurfaceMuted,
        )
        Spacer(Modifier.height(SideriaSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
            PillButton("Cancel", onDismiss, style = PillStyle.Subtle, modifier = Modifier.weight(1f))
            PillButton("Start", { onStart(mode) }, style = PillStyle.Filled, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun AstroDialog(
    detail: SessionDetail,
    onDismiss: () -> Unit,
    onStart: (AstroMode, Boolean, Boolean) -> Unit,
) {
    var mode by remember {
        mutableStateOf(
            if (detail.manifest.timelapse?.skyPreset ==
                io.github.mrdarkdebug.siderea.core.capture.session.SkyPreset.STAR_TRAILS
            ) {
                AstroMode.TRAILS
            } else {
                AstroMode.STACK
            },
        )
    }
    var darks by remember { mutableStateOf(detail.darkFrames > 0) }
    var brighten by remember { mutableStateOf(true) }
    Sheet(onDismiss) {
        Text("SKY PROCESSING", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        Spacer(Modifier.height(SideriaSpacing.sm))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
        ) {
            AstroMode.entries.forEach {
                ChipButton(it.label.uppercase(), { mode = it }, selected = it == mode)
            }
        }
        Spacer(Modifier.height(SideriaSpacing.xs))
        Text(mode.description, style = Siderea.text.readoutSmall, color = Siderea.palette.onBackground)
        Spacer(Modifier.height(SideriaSpacing.sm))
        if (detail.darkFrames > 0) {
            ChipButton("SUBTRACT ${detail.darkFrames} DARK FRAMES", { darks = !darks }, selected = darks)
        }
        ChipButton("BRIGHTEN THE SKY", { brighten = !brighten }, selected = brighten)
        Spacer(Modifier.height(SideriaSpacing.sm))
        Text(
            if (mode == AstroMode.STACK) {
                "Needs at least six clear stars per frame. Frames that can't be matched are left out and listed. " +
                    "Saves a JPEG and a 16-bit TIFF."
            } else {
                "Uses ${detail.jpegFrames} frames. Saves a JPEG and a TIFF."
            },
            style = Siderea.text.caption,
            color = Siderea.palette.onSurfaceMuted,
        )
        Spacer(Modifier.height(SideriaSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
            PillButton("Cancel", onDismiss, style = PillStyle.Subtle, modifier = Modifier.weight(1f))
            PillButton(
                "Start",
                { onStart(mode, darks && detail.darkFrames > 0, brighten) },
                style = PillStyle.Filled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun DarkFramesDialog(
    detail: SessionDetail,
    cameraBusy: Boolean,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
) {
    var count by remember { mutableStateOf(darkCountChoices[1]) }
    Sheet(onDismiss) {
        Text("DARK FRAMES", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        Spacer(Modifier.height(SideriaSpacing.sm))
        Text(
            "Cover the lens completely (lens cap, or a dark cloth held over it) and keep the phone where it is. " +
                "Siderea takes frames at the same exposure and ISO as this session, " +
                "one after another, and keeps them with it.",
            style = Siderea.text.readoutSmall,
            color = Siderea.palette.onBackground,
        )
        Spacer(Modifier.height(SideriaSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs)) {
            darkCountChoices.forEach { ChipButton("$it FRAMES", { count = it }, selected = it == count) }
        }
        if (detail.darkFrames > 0) {
            Spacer(Modifier.height(SideriaSpacing.xs))
            Text(
                "${detail.darkFrames} dark frames are already saved; these are added to them.",
                style = Siderea.text.caption,
                color = Siderea.palette.onSurfaceMuted,
            )
        }
        if (cameraBusy) {
            Spacer(Modifier.height(SideriaSpacing.xs))
            Text(
                "A session is running. Stop it first.",
                style = Siderea.text.caption,
                color = Siderea.palette.accent,
            )
        }
        Spacer(Modifier.height(SideriaSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm)) {
            PillButton("Cancel", onDismiss, style = PillStyle.Subtle, modifier = Modifier.weight(1f))
            PillButton(
                "Start",
                { onStart(count) },
                style = PillStyle.Filled,
                enabled = !cameraBusy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Sheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Siderea.palette.background.copy(alpha = SCRIM))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SideriaCard(Modifier.padding(SideriaSpacing.lg).clickable(enabled = false) {}) {
            Column(Modifier.heightIn(max = SHEET_MAX_DP.dp).verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}
