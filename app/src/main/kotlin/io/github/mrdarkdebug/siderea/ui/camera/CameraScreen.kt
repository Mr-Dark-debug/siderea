package io.github.mrdarkdebug.siderea.ui.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.mrdarkdebug.siderea.capture.RunState
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraFormat
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.camera.control.WbMode
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineState
import io.github.mrdarkdebug.siderea.core.capture.timelapse.PreflightFix
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.IconTarget
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.ReadoutCell
import io.github.mrdarkdebug.siderea.core.ui.components.ShutterButton
import io.github.mrdarkdebug.siderea.core.ui.theme.CappedFontScale
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
    onOpenSessions: (String?) -> Unit,
    onOpenGallery: () -> Unit,
    onCaptureBusyChanged: (Boolean) -> Unit = {},
    keys: ShutterKeyBus,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val motion by viewModel.motionState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.capture, state.run) {
        onCaptureBusyChanged(state.isCapturing || state.run is RunState.Running)
    }
    DisposableEffect(Unit) { onDispose { onCaptureBusyChanged(false) } }

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

    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Re-judge the checklist now that the answer is known.
            viewModel.openPreflight()
        }
    var interruptedDismissed by remember { mutableStateOf(false) }
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
                onOpenLastPhoto = onOpenGallery,
                onDismissMessage = viewModel::dismissMessage,
                onRetry = viewModel::retryOpen,
                onTimelapse = viewModel::setTimelapse,
                onSkyPreset = viewModel::setSkyPreset,
                onAstroTimelapse = viewModel::setAstroTimelapse,
                onMeasure = viewModel::measureOverhead,
                onOpenSessions = { onOpenSessions(null) },
                onPreflightStart = viewModel::confirmStart,
                onPreflightCancel = viewModel::dismissPreflight,
                onPreflightFix = { fix ->
                    val packageUri = Uri.fromParts("package", context.packageName, null)
                    when (fix) {
                        PreflightFix.NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        PreflightFix.EXACT_ALARMS -> {
                            openSystemScreen(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
                        }

                        PreflightFix.BATTERY_OPTIMISATION -> {
                            openSystemScreen(
                                context,
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                            )
                        }

                        PreflightFix.AIRPLANE_MODE -> {
                            openSystemScreen(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                        }

                        PreflightFix.LOCK_FOCUS -> {
                            viewModel.setFocusManual(true)
                            viewModel.openPreflight()
                        }

                        PreflightFix.NONE -> {
                            Unit
                        }
                    }
                },
                onStop = viewModel::stopTimelapse,
                onOpenFinished = { id ->
                    viewModel.dismissFinished()
                    onOpenSessions(id)
                },
                onDismissFinished = viewModel::dismissFinished,
                onResume = viewModel::resumeInterrupted,
                onFinalize = viewModel::finalizeInterrupted,
            )
        }

    Box(Modifier.fillMaxSize()) {
        val run = state.run
        when {
            run is RunState.Running -> {
                RunningScreen(run, actions.onStop)
            }

            run is RunState.Finished -> {
                FinishedScreen(run, { actions.onOpenFinished(run.sessionId) }, actions.onDismissFinished)
            }

            state.permission == PermissionState.DENIED -> {
                PermissionGate(
                    onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onSettings = { openAppSettings(context) },
                    onGallery = onOpenGallery,
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
        state.preflight?.let { items ->
            PreflightDialog(items, actions.onPreflightFix, actions.onPreflightStart, actions.onPreflightCancel)
        }
        val interrupted = state.interrupted.firstOrNull()
        if (interrupted != null && run is RunState.Idle && !interruptedDismissed) {
            InterruptedDialog(
                session = interrupted,
                onResume = { actions.onResume(interrupted.id) },
                onFinalize = { actions.onFinalize(interrupted.id) },
                onDismiss = { interruptedDismissed = true },
            )
        }
    }
}

private fun openSystemScreen(
    context: Context,
    intent: Intent,
) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // This phone has no such settings screen; the checklist item stays as a note.
    }
}

/** The camera chrome shares rows, so its text stops growing here; panels and dialogs grow fully. */
private const val CHROME_MAX_FONT_SCALE = 1.25f

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CameraContent(
    state: CameraUiState,
    analysis: io.github.mrdarkdebug.siderea.core.camera.analysis.FrameAnalysis?,
    motion: io.github.mrdarkdebug.siderea.device.MotionState,
    actions: CameraActions,
    viewModel: CameraViewModel,
) {
    val bars = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    var pro by rememberSaveable { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    LaunchedEffect(
        state.settings.isShutterManual,
        state.settings.isIsoManual,
        state.settings.focusMode,
        state.settings.whiteBalance.mode,
    ) {
        if (state.settings.isShutterManual || state.settings.isIsoManual ||
            state.settings.focusMode == FocusMode.MANUAL || state.settings.whiteBalance.mode != WbMode.AUTO
        ) {
            pro = true
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = bars.calculateTopPadding(), bottom = nav.calculateBottomPadding()),
    ) {
        CappedFontScale(CHROME_MAX_FONT_SCALE) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTarget(Icons.Default.Settings, "Settings", actions.onSettings)
                Spacer(Modifier.weight(1f))
                ChipButton(
                    if (SkyCapturePolicy.usesSky(state)) {
                        if (state.timelapse.skyDelaySeconds ==
                            0
                        ) {
                            "No delay"
                        } else {
                            "Delay ${state.timelapse.skyDelaySeconds}s"
                        }
                    } else {
                        if (state.timerSeconds == 0) "Timer off" else "${state.timerSeconds}s"
                    },
                    {
                        if (SkyCapturePolicy.usesSky(state)) {
                            actions.onTimelapse {
                                val next = (skyStartDelays.indexOf(it.skyDelaySeconds) + 1) % skyStartDelays.size
                                it.copy(skyDelaySeconds = skyStartDelays[next])
                            }
                        } else {
                            actions.onCycleTimer()
                        }
                    },
                    selected =
                        if (SkyCapturePolicy.usesSky(state)) {
                            state.timelapse.skyDelaySeconds > 0
                        } else {
                            state.timerSeconds >
                                0
                        },
                    description =
                        if (SkyCapturePolicy.usesSky(state)) {
                            "Sky start delay ${state.timelapse.skyDelaySeconds} seconds. Tap to change."
                        } else {
                            timerDescription(state.timerSeconds)
                        },
                )
                IconTarget(Icons.Default.Tune, "Camera tools", { tools = true })
            }
        }
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
        CappedFontScale(CHROME_MAX_FONT_SCALE) {
            Column {
                if (!SkyCapturePolicy.usesSky(state)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChipButton("Auto", {
                            actions.onShutterManual(false)
                            actions.onIsoManual(false)
                            actions.onFocusManual(false)
                            actions.onWhiteBalance(state.settings.whiteBalance.copy(mode = WbMode.AUTO))
                            actions.onClosePanel()
                            pro = false
                        }, selected = !pro, modifier = Modifier.weight(1f))
                        ChipButton("Pro", { pro = true }, selected = pro, modifier = Modifier.weight(1f))
                        if (!pro) {
                            ChipButton(CameraFormat.evLabel(state.settings.evStops), {
                                actions.onTogglePanel(ControlPanel.EV)
                            }, description = "EV exposure brightness")
                        }
                    }
                    if (pro) ReadoutRow(state, actions)
                }
                if (state.device.thermalWarning) StatusLine(state)
                ModeStrip(state.mode, actions.onSelectMode)
                BottomRow(state, actions, viewModel)
            }
        }
    }
    if (tools) {
        ModalBottomSheet(onDismissRequest = { tools = false }, containerColor = Siderea.palette.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Camera tools", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipButton(CameraSettingsOps.formatLabel(state.settings.format), actions.onCycleFormat)
                    ChipButton(state.aspect.label, actions.onCycleAspect)
                    ChipButton(
                        "Night view",
                        { actions.onAids { it.copy(nightView = !it.nightView) } },
                        selected = state.aids.nightView,
                    )
                }
                PillButton("Grid & focus aids", {
                    tools = false
                    actions.onTogglePanel(ControlPanel.AIDS)
                }, modifier = Modifier.fillMaxWidth())
                PillButton(
                    "Sessions & exports",
                    {
                        tools = false
                        actions.onOpenSessions()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SideriaSpacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CameraMode.entries.forEach { mode ->
            val available = mode.availableSince == null
            Box(
                Modifier
                    .height(48.dp)
                    .clip(SideriaShapes.pill)
                    .clickable(role = Role.Tab) { onSelect(mode) }
                    .padding(horizontal = 12.dp)
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
            description =
                when (state.mode) {
                    CameraMode.TIMELAPSE -> "Start timelapse"
                    CameraMode.ASTRO -> "Start astro session"
                    CameraMode.LONG_EXPOSURE -> "Start long exposure"
                    else -> "Take photo"
                },
            busyDescription = if (state.capture is CaptureUi.Countdown) "Cancel timer" else "Stop exposure",
            progress = if (capturing) (progress ?: 0f) else null,
            enabled = state.engine is EngineState.Ready && state.capture != CaptureUi.Saving,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (!state.isCapturing && state.lenses.any { it.facing != state.lens?.facing }) {
                IconTarget(Icons.Default.Cameraswitch, "Switch camera", actions.onFlip)
            }
        }
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
            .clickable(role = Role.Button, onClickLabel = "Open gallery") { onOpen() }
            .semantics { contentDescription = "Gallery" },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (bitmap == null) {
            androidx.compose.material3.Icon(Icons.Default.PhotoLibrary, null, tint = Siderea.palette.onBackground)
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
    onGallery: () -> Unit,
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
            "Take photos on your phone. Your photos stay on this device.",
            style = Siderea.text.readoutSmall,
            color = Siderea.palette.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        PillButton("Allow camera", onAllow, style = PillStyle.Filled)
        PillButton("Open gallery", onGallery, style = PillStyle.Outlined)
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
