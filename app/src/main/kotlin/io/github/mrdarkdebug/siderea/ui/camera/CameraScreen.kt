package io.github.mrdarkdebug.siderea.ui.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraFormat
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.camera.control.WbMode
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineState
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.IconTarget
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.ReadoutCell
import io.github.mrdarkdebug.siderea.core.ui.components.ShutterButton
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import io.github.mrdarkdebug.siderea.device.DeviceStatus
import io.github.mrdarkdebug.siderea.device.ShutterKeyBus
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@Composable
fun CameraScreen(
    onOpenSettings: () -> Unit,
    keys: ShutterKeyBus,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val motion by viewModel.motionState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onPermissionResult(granted)
        }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onPermissionResult(true) else permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onForeground(true)
        // The camera may have been granted in system settings while we were away.
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted && state.permission != PermissionState.GRANTED) viewModel.onPermissionResult(true)
        onPauseOrDispose { viewModel.onForeground(false) }
    }
    DisposableEffect(keys) {
        keys.enabled = true
        onDispose { keys.enabled = false }
    }

    val actions =
        remember(viewModel) {
            CameraActions(
                onShutter = viewModel::onShutterPressed,
                onTogglePanel = viewModel::togglePanel,
                onClosePanel = viewModel::closePanel,
                onSelectLens = viewModel::selectLens,
                onFlip = viewModel::flipFacing,
                onCycleFormat = viewModel::cycleFormat,
                onCycleAspect = viewModel::cycleAspect,
                onCycleTimer = viewModel::cycleTimer,
                onAids = viewModel::setAids,
                onShutterManual = viewModel::setShutterManual,
                onIsoManual = viewModel::setIsoManual,
                onShutterValue = viewModel::setShutter,
                onIsoValue = viewModel::setIso,
                onEv = viewModel::setEv,
                onFocusManual = viewModel::setFocusManual,
                onFocusValue = viewModel::setFocus,
                onWhiteBalance = viewModel::setWhiteBalance,
                onSelectMode = viewModel::selectMode,
                onTapFocus = viewModel::tapToFocus,
                onSettings = onOpenSettings,
                onOpenLastPhoto = { state.lastPhotoUri?.let { openPhoto(context, it) } },
                onDismissMessage = viewModel::dismissMessage,
                onRetry = viewModel::retryOpen,
            )
        }

    Box(Modifier.fillMaxSize()) {
        when {
            state.permission == PermissionState.DENIED -> {
                PermissionGate(
                    onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onSettings = { openAppSettings(context) },
                )
            }

            state.capabilityError != null -> {
                FatalMessage(state.capabilityError.orEmpty(), onOpenSettings)
            }

            else -> {
                CameraContent(state, analysis, motion, actions, viewModel)
            }
        }
        MessageBanner(state.message, actions.onDismissMessage, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun CameraContent(
    state: CameraUiState,
    analysis: io.github.mrdarkdebug.siderea.core.camera.analysis.FrameAnalysis?,
    motion: io.github.mrdarkdebug.siderea.device.MotionState,
    actions: CameraActions,
    viewModel: CameraViewModel,
) {
    val bars = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = bars.calculateTopPadding(), bottom = nav.calculateBottomPadding()),
    ) {
        TopRow(state, actions)
        Viewfinder(
            state = state,
            analysis = analysis,
            motion = motion,
            actions = actions,
            onTexture = viewModel::onViewfinderAvailable,
            onTextureDestroyed = viewModel::onViewfinderDestroyed,
            onPixels = viewModel::onPreviewPixels,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = SideriaSpacing.md),
        )
        EngineProblem(state, actions.onRetry)
        ReadoutRow(state, actions)
        StatusLine(state)
        ModeStrip(state.mode, actions.onSelectMode)
        BottomRow(state, actions, viewModel)
    }
}

@Composable
private fun TopRow(
    state: CameraUiState,
    actions: CameraActions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SideriaSpacing.sm, vertical = SideriaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xxs),
    ) {
        IconTarget(Icons.Default.Settings, "Settings", actions.onSettings)
        Spacer(Modifier.weight(1f))
        ChipButton(
            CameraSettingsOps.formatLabel(state.settings.format),
            actions.onCycleFormat,
            description = "Photo format ${CameraSettingsOps.formatLabel(state.settings.format)}. Tap to change.",
        )
        ChipButton(
            state.aspect.label,
            actions.onCycleAspect,
            description = "Frame shape ${state.aspect.label}. Tap to change.",
        )
        ChipButton(
            if (state.timerSeconds == 0) "TIMER" else "${state.timerSeconds}s",
            actions.onCycleTimer,
            selected = state.timerSeconds > 0,
            description = timerDescription(state.timerSeconds),
        )
        ChipButton(
            "NIGHT",
            { actions.onAids { it.copy(nightView = !it.nightView) } },
            selected = state.aids.nightView,
            description = "Night view: brighten the viewfinder only",
        )
        ChipButton(
            "AIDS",
            { actions.onTogglePanel(ControlPanel.AIDS) },
            selected = state.panel == ControlPanel.AIDS,
            description = "Viewfinder aids: grid, level, peaking, zebra, histogram",
        )
    }
}

private fun timerDescription(seconds: Int): String =
    "Self timer " + (if (seconds == 0) "off" else "$seconds seconds") + ". Tap to change."

@Composable
private fun ReadoutRow(
    state: CameraUiState,
    actions: CameraActions,
) {
    val s = state.settings
    val auto = !s.isShutterManual
    val cells =
        listOf(
            Triple(
                "SS",
                CameraFormat.shutterCompact(state.effectiveShutterNs) + if (auto) " A" else "",
                ControlPanel.SHUTTER,
            ),
            Triple("ISO", state.effectiveIso.toString() + if (!s.isIsoManual) " A" else "", ControlPanel.ISO),
            Triple("EV", if (CameraSettingsOps.evApplies(s)) CameraFormat.evLabel(s.evStops) else "—", ControlPanel.EV),
            Triple("WB", wbText(s.whiteBalance), ControlPanel.WB),
            Triple(
                "FOCUS",
                if (s.focusMode ==
                    FocusMode.AUTO
                ) {
                    "AF"
                } else {
                    CameraFormat.focusLabel(s.focusDiopters)
                },
                ControlPanel.FOCUS,
            ),
        )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SideriaSpacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        cells.forEach { (label, value, panel) ->
            val manual =
                when (panel) {
                    ControlPanel.SHUTTER -> s.isShutterManual
                    ControlPanel.ISO -> s.isIsoManual
                    ControlPanel.FOCUS -> s.focusMode == FocusMode.MANUAL
                    ControlPanel.WB -> s.whiteBalance.mode != WbMode.AUTO
                    else -> s.evStops != 0f
                }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(SideriaShapes.small)
                    .background(if (state.panel == panel) Siderea.palette.surfaceRaised else Siderea.palette.background)
                    .clickable(role = Role.Button, onClickLabel = "Adjust $label") { actions.onTogglePanel(panel) },
                contentAlignment = Alignment.Center,
            ) {
                ReadoutCell(label = label, value = value, highlighted = manual)
            }
        }
    }
}

private fun wbText(wb: io.github.mrdarkdebug.siderea.core.camera.control.WhiteBalance): String =
    when (wb.mode) {
        WbMode.AUTO -> "AUTO"
        WbMode.DAYLIGHT -> "DAY"
        WbMode.CLOUDY -> "CLOUD"
        WbMode.TUNGSTEN -> "TUNG"
        WbMode.FLUORESCENT -> "FLUOR"
        WbMode.SHADE -> "SHADE"
        WbMode.KELVIN -> "${wb.kelvin}K"
    }

@Composable
private fun StatusLine(state: CameraUiState) {
    val d = state.device
    val parts =
        buildList {
            d.batteryPercent?.let { add("BAT $it%" + if (d.charging) " ⚡" else "") }
            d.freeBytes?.let { add("${DeviceStatus.formatBytes(it)} FREE") }
            if (d.thermalWarning) add("PHONE WARM")
            state.ready?.let { ready ->
                val size = ready.rawSize ?: ready.jpegSize
                if (size != null) add("${size.width}×${size.height}")
            }
        }
    Text(
        text = parts.joinToString("  ·  "),
        style = Siderea.text.caption,
        color = if (d.thermalWarning) Siderea.palette.danger else Siderea.palette.onSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = SideriaSpacing.xs),
    )
}

@Composable
private fun ModeStrip(
    selected: CameraMode,
    onSelect: (CameraMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SideriaSpacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CameraMode.entries.forEach { mode ->
            val available = mode.availableSince == null
            Box(
                Modifier
                    .height(44.dp)
                    .clip(SideriaShapes.pill)
                    .clickable(role = Role.Tab) { onSelect(mode) }
                    .padding(horizontal = SideriaSpacing.md)
                    .semantics {
                        if (!available) contentDescription = "${mode.label}, arrives in ${mode.availableSince}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    style = Siderea.text.caption,
                    color =
                        when {
                            mode == selected -> Siderea.palette.accent
                            available -> Siderea.palette.onBackground
                            else -> Siderea.palette.onSurfaceMuted
                        },
                )
            }
        }
    }
}

@Composable
private fun BottomRow(
    state: CameraUiState,
    actions: CameraActions,
    viewModel: CameraViewModel,
) {
    val progress = rememberExposureProgress(state.capture)
    val capturing = state.capture != CaptureUi.Idle
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SideriaSpacing.xl, vertical = SideriaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Equal-weight side slots keep the shutter exactly centred however wide the lens chips get.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            LastPhotoThumb(state.lastPhotoUri, actions.onOpenLastPhoto, viewModel)
        }
        ShutterButton(
            onClick = actions.onShutter,
            description = "Take photo",
            busyDescription = if (state.capture is CaptureUi.Countdown) "Cancel timer" else "Stop exposure",
            progress = if (capturing) (progress ?: 0f) else null,
            enabled = state.engine is EngineState.Ready && state.capture != CaptureUi.Saving,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            LensColumn(state, actions)
        }
    }
}

@Composable
private fun LensColumn(
    state: CameraUiState,
    actions: CameraActions,
) {
    val current = state.lens ?: return
    val sameFacing = state.lenses.filter { it.facing == current.facing }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SideriaSpacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs)) {
            sameFacing.forEach { lens ->
                ChipButton(
                    text = lens.zoomLabel,
                    onClick = { actions.onSelectLens(lens.key) },
                    selected = lens.key == current.key,
                    enabled = !state.isCapturing,
                    description = "${lens.zoomLabel} lens",
                )
            }
        }
        ChipButton(
            text = if (current.isFront) "FRONT" else "BACK",
            onClick = actions.onFlip,
            enabled = !state.isCapturing && state.lenses.any { it.facing != current.facing },
            description = "Switch between front and back camera",
        )
    }
}

@Composable
private fun LastPhotoThumb(
    uri: String?,
    onOpen: () -> Unit,
    viewModel: CameraViewModel,
) {
    val bitmap by produceState<Bitmap?>(null, uri) { value = uri?.let { viewModel.thumbnail(it) } }
    Box(
        Modifier
            .size(56.dp)
            .clip(SideriaShapes.small)
            .background(Siderea.palette.surfaceRaised)
            .clickable(enabled = uri != null, role = Role.Button, onClickLabel = "Open last photo") { onOpen() }
            .semantics { contentDescription = if (uri == null) "No photo yet" else "Last photo. Open." },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun EngineProblem(
    state: CameraUiState,
    onRetry: () -> Unit,
) {
    val failed = state.engine as? EngineState.Failed ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SideriaSpacing.lg, vertical = SideriaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
    ) {
        Text(failed.message, style = Siderea.text.readoutSmall, color = Siderea.palette.danger)
        PillButton("Try again", onRetry, style = PillStyle.Outlined)
    }
}

@Composable
private fun MessageBanner(
    message: UiMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    LaunchedEffect(message.id) {
        delay(MESSAGE_MS)
        onDismiss()
    }
    Box(
        modifier
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                start = SideriaSpacing.lg,
                end = SideriaSpacing.lg,
            ).clip(SideriaShapes.medium)
            .background(Siderea.palette.surfaceRaised)
            .clickable(role = Role.Button, onClickLabel = "Dismiss", onClick = onDismiss)
            .padding(SideriaSpacing.lg),
    ) {
        Text(message.text, style = Siderea.text.readoutSmall, color = Siderea.palette.onBackground)
    }
}

private const val MESSAGE_MS = 7_000L

@Composable
private fun PermissionGate(
    onAllow: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(SideriaSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Siderea needs the camera", style = Siderea.text.readout, color = Siderea.palette.onBackground)
        Text(
            "Photos are taken on this phone and saved to Pictures/Siderea. Nothing is uploaded, " +
                "and the camera is only used while the app is open.",
            style = Siderea.text.readoutSmall,
            color = Siderea.palette.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        PillButton("Allow camera", onAllow, style = PillStyle.Filled)
        PillButton("Open app settings", onSettings, style = PillStyle.Subtle)
    }
}

@Composable
private fun FatalMessage(
    message: String,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(SideriaSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = Siderea.text.readoutSmall, color = Siderea.palette.danger, textAlign = TextAlign.Center)
        PillButton("Open settings", onSettings, style = PillStyle.Outlined)
    }
}

private fun openPhoto(
    context: Context,
    uri: String,
) {
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No gallery app can open it; the photo is still saved.
    }
}

private fun openAppSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing to do.
    }
}
