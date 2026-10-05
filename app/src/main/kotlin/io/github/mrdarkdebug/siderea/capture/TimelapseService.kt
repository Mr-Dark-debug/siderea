package io.github.mrdarkdebug.siderea.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.view.Surface
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityRepository
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityState
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.engine.AspectRatio
import io.github.mrdarkdebug.siderea.core.camera.engine.CameraEngine
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineState
import io.github.mrdarkdebug.siderea.core.camera.engine.Lens
import io.github.mrdarkdebug.siderea.core.camera.engine.LensCatalog
import io.github.mrdarkdebug.siderea.core.camera.engine.OpenParams
import io.github.mrdarkdebug.siderea.core.camera.engine.RequestPlanner
import io.github.mrdarkdebug.siderea.core.capture.session.AppSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.CameraSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.DeviceSnapshot
import io.github.mrdarkdebug.siderea.core.capture.session.FrameRecord
import io.github.mrdarkdebug.siderea.core.capture.session.RequestedCapture
import io.github.mrdarkdebug.siderea.core.capture.session.SessionHandle
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SessionManifest
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStore
import io.github.mrdarkdebug.siderea.core.capture.timelapse.GuardInputs
import io.github.mrdarkdebug.siderea.core.capture.timelapse.MovementDetector
import io.github.mrdarkdebug.siderea.core.capture.timelapse.OverheadEstimate
import io.github.mrdarkdebug.siderea.core.capture.timelapse.RunResult
import io.github.mrdarkdebug.siderea.core.capture.timelapse.RunnerListener
import io.github.mrdarkdebug.siderea.core.capture.timelapse.TimelapseRunner
import io.github.mrdarkdebug.siderea.core.data.settings.OverheadRepository
import io.github.mrdarkdebug.siderea.device.DeviceMotion
import io.github.mrdarkdebug.siderea.device.DeviceStatusReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

/**
 * Runs a timelapse as a camera foreground service, so it keeps going with the screen off and the app closed.
 *
 * Start it with [start] while the app is on screen: Android only lets a camera foreground service begin
 * from the foreground. Stop it with [ACTION_STOP] (the notification's Stop button does exactly that).
 */
@AndroidEntryPoint
class TimelapseService : Service() {
    @Inject lateinit var engine: CameraEngine

    @Inject lateinit var store: SessionStore

    @Inject lateinit var sessionState: CaptureSessionState

    @Inject lateinit var capabilities: CapabilityRepository

    @Inject lateinit var deviceStatus: DeviceStatusReader

    @Inject lateinit var motion: DeviceMotion

    @Inject lateinit var overheads: OverheadRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val headless = HeadlessPreview()
    private lateinit var notifications: SessionNotifications

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = SessionNotifications(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                requestStop()
            }

            ACTION_START -> {
                intent.getStringExtra(EXTRA_REQUEST)?.let { text ->
                    val request = runCatching { json.decodeFromString(LaunchRequest.serializer(), text) }.getOrNull()
                    if (request != null) begin(request) else stopSelf()
                } ?: stopSelf()
            }

            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(request: LaunchRequest) {
        if (job?.isActive == true) return
        val placeholder =
            RunState.Running(
                sessionId = request.resumeSessionId.orEmpty(),
                name = request.name ?: SessionKind.TIMELAPSE.title,
                kind = SessionKind.TIMELAPSE,
                format = request.settings.format,
                frames = 0,
                plannedFrames = request.config.plannedFrames,
                startedAtElapsedMs = SystemClock.elapsedRealtime(),
                intervalMs = request.config.intervalMs,
                lastPreviewPath = null,
                lastFrameAtElapsedMs = null,
                notices = emptyList(),
                overheadMs = null,
                overheadMeasured = false,
                keepScreenOn = request.keepScreenOn,
            )
        sessionState.set(placeholder)
        ServiceCompat.startForeground(
            this,
            SessionNotifications.ONGOING_ID,
            notifications.progress(placeholder),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
        acquireWakeLock()
        motion.start()
        job = scope.launch { run(request) }
    }

    private fun requestStop() {
        sessionState.update { it.copy(stopping = true) }
        job?.cancel() ?: stopSelf()
    }

    // region the session

    private suspend fun run(request: LaunchRequest) {
        var session: SessionHandle? = null
        var result: RunResult? = null
        var failure: String? = null
        try {
            val lens = findLens(request) ?: error("This lens is no longer available.")
            val limits = ExposureLimits.from(lens.info, Build.VERSION.SDK_INT)
            val format = request.settings.format
            openCamera(lens, request)
            val ready = (engine.state.value as? EngineState.Ready)?.info ?: error("The camera didn't open.")
            val capabilities = ready.capabilities
            primePreview(request, limits, capabilities)
            session = openSession(request, lens)
            val movement = MovementDetector(MOVED_DEGREES)
            val motionJob =
                scope.launch {
                    motion.state.collect {
                        movement.add(
                            SystemClock.elapsedRealtime(),
                            it.rollDegrees,
                            it.elevationDegrees,
                        )
                    }
                }
            // Let autofocus and white balance settle, then treat this pose as the reference.
            delay(WARM_UP_MS)
            movement.markReference()
            val capturer =
                EngineFrameCapturer(
                    engine = engine,
                    session = session,
                    request = request,
                    limits = limits,
                    capabilities = { (engine.state.value as? EngineState.Ready)?.info?.capabilities ?: capabilities },
                    format = format,
                    movement = movement,
                    reopen = { reopenCamera(lens, request) },
                    onPreview = ::onPreview,
                )
            val runner =
                TimelapseRunner(
                    config = request.config,
                    session = session,
                    format = format,
                    capturer = capturer,
                    guards = { bytes -> guardInputs(bytes) },
                    clock = AndroidRunnerClock(this),
                    listener = listener(session),
                )
            val (startIndex, offset) = resumePoint(session, request)
            try {
                result = runner.run(startIndex, offset)
            } finally {
                motionJob.cancel()
            }
        } catch (_: CancellationException) {
            result = null
        } catch (e: IllegalStateException) {
            failure = e.message ?: "The session couldn't start."
        }
        withContext(NonCancellable) { finish(session, result, failure) }
    }

    private fun findLens(request: LaunchRequest): Lens? {
        val report = (capabilities.state.value as? CapabilityState.Ready)?.report ?: return null
        return LensCatalog.from(report).firstOrNull { it.key == request.lensKey }
    }

    private suspend fun openCamera(
        lens: Lens,
        request: LaunchRequest,
    ) {
        val aspect = AspectRatio.entries.firstOrNull { it.name == request.aspect } ?: AspectRatio.FOUR_THREE
        engine.open(OpenParams(lens, request.settings.format, aspect) { w, h -> headless.surface(w, h) })
        val state = withTimeoutOrNull(OPEN_TIMEOUT_MS) { engine.state.first { it !is EngineState.Opening } }
        if (state is EngineState.Failed) error(state.message)
        if (state !is EngineState.Ready) error("The camera didn't open in time.")
    }

    private suspend fun reopenCamera(
        lens: Lens,
        request: LaunchRequest,
    ): Boolean {
        repeat(REOPEN_ATTEMPTS) {
            runCatching { openCamera(lens, request) }
            if (engine.state.value is EngineState.Ready) return true
            delay(REOPEN_DELAY_MS)
        }
        return false
    }

    /** Starts the repeating request so autofocus, auto white balance and (if unlocked) auto exposure run. */
    private fun primePreview(
        request: LaunchRequest,
        limits: ExposureLimits,
        capabilities: io.github.mrdarkdebug.siderea.core.camera.engine.EngineCapabilities,
    ) {
        val s = lockedSettings(request)
        engine.updatePreviewPlan(RequestPlanner.plan(s, s.shutterNs, s.iso, limits, capabilities, forPreview = true))
    }

    private fun lockedSettings(request: LaunchRequest): CaptureSettings =
        if (request.config.lockExposure) {
            request.settings.copy(
                exposureMode = ExposureMode.MANUAL,
                shutterNs = request.lockedShutterNs,
                iso = request.lockedIso,
            )
        } else {
            request.settings.copy(exposureMode = ExposureMode.AUTO)
        }

    private fun openSession(
        request: LaunchRequest,
        lens: Lens,
    ): SessionHandle {
        request.resumeSessionId?.let { id ->
            store.open(id)?.let { existing ->
                existing.updateManifest { it.copy(status = SessionStatus.RUNNING, finishedAtEpochMs = null) }
                existing.addEvent("Resumed after an interruption.")
                return existing
            }
        }
        val info = packageManager.getPackageInfo(packageName, 0)
        return store.create(SessionKind.TIMELAPSE) { id, created ->
            SessionManifest(
                id = id,
                name = request.name ?: "Timelapse " + id.substringBefore("_Timelapse").replace('_', ' '),
                kind = SessionKind.TIMELAPSE,
                status = SessionStatus.RUNNING,
                createdAtEpochMs = created,
                app = AppSnapshot("Siderea", info.versionName ?: "unknown", info.longVersionCode),
                device = DeviceSnapshot(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                camera = CameraSnapshot(lens.key, lens.openId, lens.physicalId, lens.zoomLabel, lens.facing.name),
                requested = RequestedCapture(lockedSettings(request), request.aspect),
                timelapse = request.config,
            )
        }
    }

    private fun resumePoint(
        session: SessionHandle,
        request: LaunchRequest,
    ): Pair<Int, Long> {
        if (request.resumeSessionId == null) return 0 to 0L
        val last = session.frames.maxByOrNull { it.index } ?: return 0 to 0L
        return (last.index + 1) to (last.plannedAtMs + request.config.intervalMs)
    }

    private fun guardInputs(bytesPerFrame: Long): GuardInputs {
        val d = deviceStatus.read()
        return GuardInputs(d.thermalStatus, d.batteryPercent, d.charging, d.freeBytes, bytesPerFrame)
    }

    private fun listener(session: SessionHandle) =
        object : RunnerListener {
            override fun onFrame(
                record: FrameRecord,
                total: Int?,
                overhead: OverheadEstimate,
            ) {
                sessionState.update {
                    it.copy(
                        sessionId = session.id,
                        name = session.manifest.name,
                        frames = session.frames.count { f -> f.error == null },
                        lastFrameAtElapsedMs = SystemClock.elapsedRealtime(),
                        overheadMs = overhead.overheadMs,
                        overheadMeasured = overhead.measured,
                    )
                }
                (sessionState.state.value as? RunState.Running)?.let(notifications::update)
            }

            override fun onNotice(
                text: String,
                warning: Boolean,
            ) {
                sessionState.update { it.copy(notices = (it.notices + text).takeLast(MAX_NOTICES)) }
                (sessionState.state.value as? RunState.Running)?.let(notifications::update)
            }
        }

    private fun onPreview(file: File) {
        sessionState.update { it.copy(lastPreviewPath = file.absolutePath) }
    }

    private suspend fun finish(
        session: SessionHandle?,
        result: RunResult?,
        failure: String?,
    ) {
        val status =
            when {
                result is RunResult.Completed -> SessionStatus.COMPLETED
                result != null -> SessionStatus.FAILED
                failure != null -> SessionStatus.FAILED
                else -> SessionStatus.STOPPED
            }
        val message =
            when (result) {
                is RunResult.StoppedByGuard -> result.message
                is RunResult.Failed -> result.message
                else -> failure
            }
        val frames = session?.frames?.count { it.error == null } ?: 0
        val name = session?.manifest?.name ?: SessionKind.TIMELAPSE.title
        val running = sessionState.state.value as? RunState.Running
        session?.let { handle ->
            message?.let(handle::addEvent)
            handle.finish(status)
            running?.overheadMs?.takeIf { running.overheadMeasured }?.let { overheads.save(running.format.name, it) }
        }
        runCatching { engine.close() }
        headless.release()
        motion.stop()
        releaseWakeLock()
        sessionState.set(RunState.Finished(session?.id.orEmpty(), status, frames, message))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notifications.finished(name, status, frames, message)
        stopSelf()
    }

    // endregion

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock =
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "siderea:timelapse").apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_LOCK_MS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        headless.release()
        super.onDestroy()
    }

    /** A throw-away preview surface: a running session has no screen, but the camera still wants a stream. */
    private class HeadlessPreview {
        private val thread = HandlerThread("siderea-headless").apply { start() }
        private var reader: ImageReader? = null

        fun surface(
            width: Int,
            height: Int,
        ): Surface {
            reader?.close()
            val next =
                ImageReader.newInstance(
                    width,
                    height,
                    ImageFormat.PRIVATE,
                    IMAGE_BUFFERS,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
                )
            next.setOnImageAvailableListener({ it.acquireNextImage()?.close() }, Handler(thread.looper))
            reader = next
            return next.surface
        }

        fun release() {
            reader?.close()
            reader = null
        }
    }

    companion object {
        const val ACTION_START = "io.github.mrdarkdebug.siderea.action.START_TIMELAPSE"
        const val ACTION_STOP = "io.github.mrdarkdebug.siderea.action.STOP_TIMELAPSE"
        private const val EXTRA_REQUEST = "request"
        private const val OPEN_TIMEOUT_MS = 20_000L
        private const val WARM_UP_MS = 1_500L
        private const val REOPEN_ATTEMPTS = 3
        private const val REOPEN_DELAY_MS = 3_000L
        private const val MAX_NOTICES = 5
        private const val MOVED_DEGREES = 1.0f
        private const val MAX_WAKE_LOCK_MS = 24 * 60 * 60 * 1000L
        private const val IMAGE_BUFFERS = 3
        private val json = Json { ignoreUnknownKeys = true }

        /** Starts a session. Call this while Siderea is on screen. */
        fun start(
            context: Context,
            request: LaunchRequest,
        ) {
            val intent =
                Intent(context, TimelapseService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_REQUEST, json.encodeToString(LaunchRequest.serializer(), request))
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TimelapseService::class.java).setAction(ACTION_STOP))
        }
    }
}
