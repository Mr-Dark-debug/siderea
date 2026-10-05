package io.github.mrdarkdebug.siderea.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.capture.RunState
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.session.SessionSummary
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig
import io.github.mrdarkdebug.siderea.core.capture.timelapse.IntervalMath
import io.github.mrdarkdebug.siderea.core.capture.timelapse.Preflight
import io.github.mrdarkdebug.siderea.core.capture.timelapse.PreflightFix
import io.github.mrdarkdebug.siderea.core.capture.timelapse.PreflightItem
import io.github.mrdarkdebug.siderea.core.capture.timelapse.PreflightStatus
import io.github.mrdarkdebug.siderea.core.ui.components.CapabilityChip
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.RulerDial
import io.github.mrdarkdebug.siderea.core.ui.components.SegmentedPill
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaCard
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private val customIntervalsSeconds =
    listOf(
        1,
        2,
        3,
        4,
        5,
        6,
        8,
        10,
        12,
        15,
        20,
        25,
        30,
        40,
        45,
        60,
        90,
        120,
        180,
        240,
        300,
        420,
        600,
        900,
        1200,
        1800,
        2700,
        3600,
    )
private val frameChoices =
    listOf(10, 20, 30, 50, 75, 100, 150, 200, 300, 400, 500, 600, 800, 1000, 1500, 2000, 3000, 5000)
private val durationMinutes = listOf(1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 600, 720)
private val fpsChoices = listOf(24, 25, 30, 60)
private const val MS_PER_SECOND = 1_000L
private const val MS_PER_MINUTE = 60_000L
private const val SECONDS_PER_MINUTE = 60

private val presets = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L, 60_000L, 300_000L, 1_800_000L)

fun intervalLabel(ms: Long): String = if (ms < MS_PER_MINUTE) "${ms / MS_PER_SECOND} s" else "${ms / MS_PER_MINUTE} min"

private fun presetLabel(ms: Long): String =
    if (ms < MS_PER_MINUTE) "${ms / MS_PER_SECOND}s" else "${ms / MS_PER_MINUTE}m"

private fun durationLabel(ms: Long): String {
    val minutes = ms / MS_PER_MINUTE
    return if (minutes < SECONDS_PER_MINUTE) {
        "$minutes min"
    } else {
        "${minutes / SECONDS_PER_MINUTE} h" +
            if (minutes % SECONDS_PER_MINUTE == 0L) "" else " ${minutes % SECONDS_PER_MINUTE} min"
    }
}

/** Setup panel for a timelapse: interval, how long, what to lock, and a live calculator. */
@Composable
fun TimelapsePanel(
    state: CameraUiState,
    actions: CameraActions,
) {
    val setup = state.timelapse
    val scroll = rememberScrollState()
    Column(
        Modifier
            .heightIn(max = PANEL_MAX_HEIGHT)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
    ) {
        Calculator(state)
        Text("INTERVAL", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs)) {
            items(presets) { ms ->
                ChipButton(
                    presetLabel(ms),
                    { actions.onTimelapse { it.copy(intervalMs = ms, customInterval = false) } },
                    selected = setup.intervalMs == ms && !setup.customInterval,
                    description = "Interval ${intervalLabel(ms)}",
                )
            }
            item {
                ChipButton(
                    "CUSTOM",
                    { actions.onTimelapse { it.copy(customInterval = true) } },
                    selected = setup.customInterval,
                )
            }
        }
        if (setup.customInterval) {
            val labels = remember { customIntervalsSeconds.map { intervalLabel(it * MS_PER_SECOND) } }
            RulerDial(
                labels = labels,
                selectedIndex =
                    customIntervalsSeconds.indices.minByOrNull {
                        kotlin.math.abs(customIntervalsSeconds[it] * MS_PER_SECOND - setup.intervalMs)
                    } ?: 0,
                onSelectedIndexChange = { i ->
                    actions.onTimelapse {
                        it.copy(
                            intervalMs =
                                customIntervalsSeconds[i] * MS_PER_SECOND,
                        )
                    }
                },
                description = "Custom interval",
                majorEvery = 4,
            )
        }
        Text("STOP AFTER", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        SegmentedPill(
            options = StopCondition.entries,
            selected = setup.stop,
            onSelect = { stop -> actions.onTimelapse { it.copy(stop = stop) } },
            label = {
                when (it) {
                    StopCondition.FRAME_COUNT -> "FRAMES"
                    StopCondition.DURATION -> "TIME"
                    StopCondition.UNTIL_STOPPED -> "UNTIL I STOP"
                }
            },
        )
        when (setup.stop) {
            StopCondition.FRAME_COUNT -> {
                RulerDial(
                    labels = frameChoices.map { "$it" },
                    selectedIndex =
                        frameChoices.indices.minByOrNull { kotlin.math.abs(frameChoices[it] - setup.frameCount) } ?: 0,
                    onSelectedIndexChange = { i -> actions.onTimelapse { it.copy(frameCount = frameChoices[i]) } },
                    description = "Number of frames",
                    majorEvery = 4,
                )
            }

            StopCondition.DURATION -> {
                RulerDial(
                    labels = durationMinutes.map { durationLabel(it * MS_PER_MINUTE) },
                    selectedIndex =
                        durationMinutes.indices.minByOrNull {
                            kotlin.math.abs(durationMinutes[it] * MS_PER_MINUTE - setup.durationMs)
                        } ?: 0,
                    onSelectedIndexChange = { i ->
                        actions.onTimelapse {
                            it.copy(
                                durationMs =
                                    durationMinutes[i] * MS_PER_MINUTE,
                            )
                        }
                    },
                    description = "Session length",
                    majorEvery = 4,
                )
            }

            StopCondition.UNTIL_STOPPED -> {
                Hint("Runs until you press Stop or the phone has to stop it.")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs)) {
            item {
                ChipButton("LOCK EXPOSURE", {
                    actions.onTimelapse { it.copy(lockExposure = !it.lockExposure, rampExposure = false) }
                }, selected = setup.lockExposure && !setup.rampExposure)
            }
            item {
                ChipButton(
                    "RAMP EXPOSURE",
                    {
                        actions.onTimelapse {
                            it.copy(
                                rampExposure = !it.rampExposure,
                                lockExposure = it.rampExposure,
                            )
                        }
                    },
                    selected = setup.rampExposure,
                    description = "Follow changing light with a smooth exposure ramp, for sunsets and sunrises",
                )
            }
            item {
                ChipButton("SLOW WHEN HOT", {
                    actions.onTimelapse { it.copy(adaptToHeat = !it.adaptToHeat) }
                }, selected = setup.adaptToHeat)
            }
            item {
                ChipButton("KEEP SCREEN ON", {
                    actions.onTimelapse { it.copy(keepScreenOn = !it.keepScreenOn) }
                }, selected = setup.keepScreenOn)
            }
        }
        if (setup.rampExposure) {
            Text(
                if (state.settings.format == CaptureFormat.RAW) {
                    "The ramp measures each JPEG, so it can't steer RAW-only frames. Pick JPEG or RAW + JPEG."
                } else {
                    "Shutter first, then ISO up to ${TimelapseConfig.DEFAULT_RAMP_MAX_ISO}, a quarter of a stop at a " +
                        "time. Needs a lens with manual exposure; deflicker the video afterwards to finish the job."
                },
                style = Siderea.text.caption,
                color = Siderea.palette.onSurfaceMuted,
            )
        }
        Text("VIDEO FRAME RATE", style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.xs)) {
            fpsChoices.forEach { fps ->
                ChipButton("$fps", { actions.onTimelapse { it.copy(fps = fps) } }, selected = setup.fps == fps)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipButton(
                if (state.measuring) "MEASURING…" else "MEASURE OVERHEAD",
                actions.onMeasure,
                enabled = !state.measuring,
                description = "Take three test photos to measure how long each capture really takes",
            )
            ChipButton("SESSIONS", actions.onOpenSessions)
        }
        Hint("Press the shutter to run the checklist and start.")
    }
}

private val PANEL_MAX_HEIGHT = 400.dp

@Composable
private fun Hint(text: String) {
    Text(text, style = Siderea.text.readoutSmall, color = Siderea.palette.onSurfaceMuted)
}

/** What this setup will produce: frames, video length, storage, battery and whether the interval works. */
@Composable
private fun Calculator(state: CameraUiState) {
    val setup = state.timelapse
    val palette = Siderea.palette
    val config =
        remember(setup) {
            io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig(
                intervalMs = setup.intervalMs,
                stop = setup.stop,
                frameCount = setup.frameCount,
                durationMs = setup.durationMs,
                lockExposure = setup.lockExposure,
                outputFps = setup.fps,
            )
        }
    val frames = config.plannedFrames
    val size = state.ready?.let { it.rawSize ?: it.jpegSize }
    val megapixels = size?.let { it.width.toLong() * it.height / MEGA }?.toFloat() ?: DEFAULT_MEGAPIXELS
    val bytes = IntervalMath.typicalFrameBytes(state.settings.format, megapixels)
    val check = IntervalMath.check(setup.intervalMs, state.effectiveShutterNs, state.overhead)
    val duration = IntervalMath.sessionDurationMs(config)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SideriaShapes.small)
            .background(palette.surfaceRaised)
            .padding(SideriaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.xs),
    ) {
        Text(
            check.message,
            style = Siderea.text.readoutSmall,
            color = if (check.ok) palette.onBackground else palette.danger,
        )
        if (frames != null) {
            val video = IntervalMath.outputSeconds(frames, setup.fps)
            Text(
                "$frames frames  ·  ${"%.1f".format(java.util.Locale.ROOT, video)} s of video at ${setup.fps} fps",
                style = Siderea.text.readoutSmall,
                color = palette.onBackground,
            )
            val battery =
                duration?.let {
                    IntervalMath.batteryPercent(
                        it,
                        setup.intervalMs,
                        state.effectiveShutterNs,
                        state.overhead.overheadMs,
                    )
                }
            Text(
                "About ${gb(IntervalMath.storageBytes(frames, bytes))} of storage" +
                    (duration?.let { "  ·  runs ${IntervalMath.formatSeconds(it)}" } ?: "") +
                    (battery?.let { "  ·  ~${it.toInt().coerceAtLeast(1)} % battery" } ?: ""),
                style = Siderea.text.readoutSmall,
                color = palette.onSurfaceMuted,
            )
        } else {
            Text(
                "Runs until stopped, about ${gb(bytes)} per frame.",
                style = Siderea.text.readoutSmall,
                color = palette.onSurfaceMuted,
            )
        }
    }
}

private fun gb(bytes: Long): String =
    if (bytes >= GIGA) "%.1f GB".format(java.util.Locale.ROOT, bytes / GIGA.toDouble()) else "${bytes / MEGA_BYTES} MB"

private const val GIGA = 1_000_000_000L
private const val MEGA_BYTES = 1_000_000L
private const val MEGA = 1_000_000L
private const val DEFAULT_MEGAPIXELS = 12f

// ---- pre-flight ---------------------------------------------------------------------------------------

/** The checklist shown before a session starts. Fixable items carry a button. */
@Composable
fun PreflightDialog(
    items: List<PreflightItem>,
    onFix: (PreflightFix) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = Siderea.palette
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = SCRIM))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(SideriaSpacing.lg)
                .clip(SideriaShapes.large)
                .background(palette.surface)
                .clickable(enabled = false) {}
                .padding(SideriaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
        ) {
            Text("BEFORE YOU START", style = Siderea.text.caption, color = palette.onSurfaceMuted)
            LazyColumn(
                Modifier.heightIn(max = LIST_MAX_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
            ) {
                items(items, key = { it.id }) { item -> PreflightRow(item, onFix) }
            }
            val canStart = Preflight.canStart(items)
            if (!canStart) Hint("Fix the items marked FIX to start.")
            Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                PillButton("Cancel", onCancel, style = PillStyle.Subtle, modifier = Modifier.weight(1f))
                PillButton(
                    "Start",
                    onStart,
                    style = PillStyle.Filled,
                    enabled = canStart,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val LIST_MAX_HEIGHT = 420.dp
private const val SCRIM = 0.85f

@Composable
private fun PreflightRow(
    item: PreflightItem,
    onFix: (PreflightFix) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SideriaSpacing.xxs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.sm),
        ) {
            CapabilityChip(
                text =
                    when (item.status) {
                        PreflightStatus.OK -> "OK"
                        PreflightStatus.WARN -> "CHECK"
                        PreflightStatus.FAIL -> "FIX"
                    },
                on = item.status == PreflightStatus.OK,
                partial = item.status == PreflightStatus.WARN,
            )
            Text(
                item.title,
                style = Siderea.text.readoutSmall,
                color = Siderea.palette.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (item.fix != PreflightFix.NONE && item.status != PreflightStatus.OK) {
                ChipButton(fixLabel(item.fix), { onFix(item.fix) })
            }
        }
        Text(
            item.detail,
            style = Siderea.text.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = Siderea.palette.onSurfaceMuted,
        )
    }
}

private fun fixLabel(fix: PreflightFix) =
    when (fix) {
        PreflightFix.NOTIFICATIONS -> "ALLOW"
        PreflightFix.EXACT_ALARMS -> "ALLOW"
        PreflightFix.BATTERY_OPTIMISATION -> "EXEMPT"
        PreflightFix.AIRPLANE_MODE -> "SETTINGS"
        PreflightFix.LOCK_FOCUS -> "LOCK"
        PreflightFix.NONE -> ""
    }

// ---- running and finished -----------------------------------------------------------------------------

/** Full-screen progress while a session runs, with Stop and a dim-the-screen mode. */
@Composable
fun RunningScreen(
    run: RunState.Running,
    onStop: () -> Unit,
) {
    var dimmed by remember { mutableStateOf(false) }
    ScreenDimming(dimmed = dimmed, keepOn = run.keepScreenOn)
    val palette = Siderea.palette
    val nowMs = remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs.longValue = android.os.SystemClock.elapsedRealtime()
            delay(TICK_MS)
        }
    }
    val elapsed = nowMs.longValue - run.startedAtElapsedMs
    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(SideriaSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SideriaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(SideriaSpacing.xl))
            Text(if (run.stopping) "STOPPING" else "CAPTURING", style = Siderea.text.caption, color = palette.accent)
            Text(run.name, style = Siderea.text.readout, color = palette.onBackground, textAlign = TextAlign.Center)
            Text(
                text = "${run.frames}",
                style =
                    Siderea.text.readoutLarge.copy(
                        fontSize =
                            androidx.compose.ui.unit
                                .TextUnit(COUNTER_SP, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                color = palette.onBackground,
            )
            Text(
                text = run.plannedFrames?.let { "of $it frames" } ?: "frames, until you stop",
                style = Siderea.text.readoutSmall,
                color = palette.onSurfaceMuted,
            )
            run.plannedFrames?.let { total ->
                LinearProgressIndicator(
                    progress = { (run.frames / total.toFloat()).coerceIn(0f, 1f) },
                    color = palette.accent,
                    trackColor = palette.surfaceRaised,
                    modifier = Modifier.fillMaxWidth(),
                )
                val remaining = (total - run.frames).coerceAtLeast(0) * run.intervalMs
                Text(
                    "Elapsed ${clock(elapsed)}  ·  about ${clock(remaining)} left",
                    style = Siderea.text.readoutSmall,
                    color = palette.onSurfaceMuted,
                )
            } ?: Text("Elapsed ${clock(elapsed)}", style = Siderea.text.readoutSmall, color = palette.onSurfaceMuted)
            LastFrame(run.lastPreviewPath, Modifier.weight(1f, fill = false).heightIn(max = PREVIEW_MAX_HEIGHT))
            run.overheadMs?.let {
                Text(
                    "Capture overhead ${CameraFormat.exposure(
                        it * NS_PER_MS,
                    )}${if (run.overheadMeasured) " (measured)" else ""}",
                    style = Siderea.text.caption,
                    color = palette.onSurfaceMuted,
                )
            }
            run.notices.takeLast(2).forEach {
                Text(it, style = Siderea.text.readoutSmall, color = palette.danger, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SideriaSpacing.md), modifier = Modifier.fillMaxWidth()) {
                PillButton("Dim screen", { dimmed = true }, style = PillStyle.Subtle, modifier = Modifier.weight(1f))
                PillButton(
                    "Stop",
                    onStop,
                    style = PillStyle.Filled,
                    enabled = !run.stopping,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (dimmed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .clickable { dimmed = false },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${run.frames}",
                    style = Siderea.text.caption,
                    color = palette.onSurfaceMuted.copy(alpha = DIM_TEXT),
                )
            }
        }
    }
}

private const val TICK_MS = 1_000L
private const val COUNTER_SP = 88f
private const val NS_PER_MS = 1_000_000L
private const val DIM_TEXT = 0.25f
private val PREVIEW_MAX_HEIGHT = 260.dp

private fun clock(ms: Long): String {
    val total = ms / MS_PER_SECOND
    val h = total / (SECONDS_PER_MINUTE * SECONDS_PER_MINUTE)
    val m = (total / SECONDS_PER_MINUTE) % SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Dims the screen to nearly black and keeps it awake, so the phone stays out of deep sleep. */
@Composable
private fun ScreenDimming(
    dimmed: Boolean,
    keepOn: Boolean,
) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(dimmed, keepOn) {
        val window = activity.window
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes =
            window.attributes.apply {
                screenBrightness = if (dimmed) DIM_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes =
                window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}

private const val DIM_BRIGHTNESS = 0.01f

@Composable
private fun LastFrame(
    path: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = path?.let { p -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(File(p).path) } }
    }
    Box(
        modifier.clip(SideriaShapes.medium).background(Siderea.palette.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = "Most recent frame",
                contentScale = ContentScale.Fit,
            )
        }
            ?: Text(
                "Waiting for the first frame…",
                style = Siderea.text.readoutSmall,
                color = Siderea.palette.onSurfaceMuted,
                modifier = Modifier.padding(SideriaSpacing.xl),
            )
    }
}

/** Shown when a session ends: what happened and where the frames are. */
@Composable
fun FinishedScreen(
    done: RunState.Finished,
    onOpen: () -> Unit,
    onDone: () -> Unit,
) {
    val palette = Siderea.palette
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(SideriaSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SideriaSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (done.status) {
                SessionStatus.COMPLETED -> "FINISHED"
                SessionStatus.STOPPED -> "STOPPED"
                else -> "ENDED EARLY"
            },
            style = Siderea.text.caption,
            color = palette.accent,
        )
        Text("${done.frames} frames saved", style = Siderea.text.readout, color = palette.onBackground)
        done.message?.let {
            Text(
                it,
                style = Siderea.text.readoutSmall,
                color = palette.danger,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Every frame is kept. Open the session to review it.",
            style = Siderea.text.readoutSmall,
            color = palette.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        PillButton("Open session", onOpen, style = PillStyle.Filled, modifier = Modifier.fillMaxWidth())
        PillButton("Back to camera", onDone, style = PillStyle.Subtle, modifier = Modifier.fillMaxWidth())
    }
}

/** Offer to resume or finalise a session that was cut short by a crash or shutdown. */
@Composable
fun InterruptedDialog(
    session: SessionSummary,
    onResume: () -> Unit,
    onFinalize: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = Siderea.palette
    Box(
        Modifier.fillMaxSize().background(palette.background.copy(alpha = SCRIM)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SideriaCard(Modifier.padding(SideriaSpacing.lg).clickable(enabled = false) {}) {
            Text("SESSION INTERRUPTED", style = Siderea.text.caption, color = palette.accent)
            Spacer(Modifier.height(SideriaSpacing.xs))
            Text(session.name, style = Siderea.text.readout, color = palette.onBackground)
            Text(
                "${session.frameCount}" + (session.plannedFrames?.let { " of $it" } ?: "") +
                    " frames were captured before it was cut short. They are all safe.",
                style = Siderea.text.readoutSmall,
                color = palette.onSurfaceMuted,
            )
            Spacer(Modifier.height(SideriaSpacing.md))
            PillButton("Resume", onResume, style = PillStyle.Filled, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(SideriaSpacing.sm))
            PillButton(
                "Finalize what was captured",
                onFinalize,
                style = PillStyle.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SideriaSpacing.sm))
            PillButton("Decide later", onDismiss, style = PillStyle.Subtle, modifier = Modifier.fillMaxWidth())
        }
    }
}
