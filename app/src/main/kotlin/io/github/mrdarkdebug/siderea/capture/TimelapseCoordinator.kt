package io.github.mrdarkdebug.siderea.capture

import android.app.AlarmManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.camera.engine.CameraEngine
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineException
import io.github.mrdarkdebug.siderea.core.camera.engine.RequestPlan
import io.github.mrdarkdebug.siderea.core.camera.engine.StillRequest
import io.github.mrdarkdebug.siderea.core.capture.session.SessionKind
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStore
import io.github.mrdarkdebug.siderea.core.capture.session.SessionSummary
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig
import io.github.mrdarkdebug.siderea.core.capture.timelapse.IntervalMath
import io.github.mrdarkdebug.siderea.core.capture.timelapse.MovementDetector
import io.github.mrdarkdebug.siderea.core.capture.timelapse.OverheadEstimate
import io.github.mrdarkdebug.siderea.core.capture.timelapse.OverheadTracker
import io.github.mrdarkdebug.siderea.core.capture.timelapse.Preflight
import io.github.mrdarkdebug.siderea.core.capture.timelapse.PreflightInputs
import io.github.mrdarkdebug.siderea.core.capture.timelapse.PreflightItem
import io.github.mrdarkdebug.siderea.core.data.settings.OverheadRepository
import io.github.mrdarkdebug.siderea.device.DeviceMotion
import io.github.mrdarkdebug.siderea.device.DeviceStatusReader
import io.github.mrdarkdebug.siderea.ui.camera.TimelapseSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the camera screen needs from the timelapse feature that isn't drawing: measuring overhead,
 * assembling the pre-flight checklist from the phone's real state, building the service request, and
 * recovering sessions that were cut short.
 */
@Singleton
class TimelapseCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val engine: CameraEngine,
        private val store: SessionStore,
        private val runState: CaptureSessionState,
        private val overheads: OverheadRepository,
        private val motion: DeviceMotion,
        private val deviceStatus: DeviceStatusReader,
    ) {
        val run = runState.state

        private val tracker = OverheadTracker()
        private val steadiness = MovementDetector(STEADY_DEGREES)

        /** Feed the phone's tilt in while the camera screen is up so "tripod steady" has something to judge. */
        fun onMotion(state: io.github.mrdarkdebug.siderea.device.MotionState) {
            steadiness.add(SystemClock.elapsedRealtime(), state.rollDegrees, state.elevationDegrees)
        }

        suspend fun overhead(format: CaptureFormat): OverheadEstimate {
            val saved = overheads.measured.first()[format.name]
            return if (saved !=
                null
            ) {
                OverheadEstimate(saved, measured = true, samples = 1)
            } else {
                tracker.estimate(format)
            }
        }

        /**
         * Takes a few real photos at the chosen settings (kept nowhere) and times them, including writing the
         * file, so the minimum interval shown to the user is a measurement rather than a guess.
         */
        suspend fun measureOverhead(
            plan: RequestPlan,
            format: CaptureFormat,
            orientation: Int,
        ): Result<OverheadEstimate> {
            val samples = mutableListOf<Long>()
            repeat(PROBE_FRAMES) {
                val started = SystemClock.elapsedRealtime()
                val photo =
                    try {
                        engine.capture(StillRequest(plan, format, orientation))
                    } catch (e: EngineException) {
                        return Result.failure(e)
                    }
                withContext(Dispatchers.IO) {
                    val scratch = java.io.File.createTempFile("probe-", ".bin", context.cacheDir)
                    try {
                        photo.jpeg?.let { scratch.writeBytes(it) }
                        photo.dng?.let { scratch.appendBytes(it.readBytes()) }
                    } finally {
                        scratch.delete()
                        photo.dng?.delete()
                    }
                }
                val total = SystemClock.elapsedRealtime() - started
                tracker.record(total, plan.shutterNs)
                samples += (total - plan.shutterNs / NS_PER_MS).coerceAtLeast(0)
            }
            // The first capture often includes warm-up; the worst case of the rest is what a long run will see.
            val worst = samples.drop(1).ifEmpty { samples }.max()
            val estimate = OverheadEstimate((worst * MARGIN).toLong(), measured = true, samples = samples.size)
            overheads.save(format.name, estimate.overheadMs)
            return Result.success(estimate)
        }

        fun config(setup: TimelapseSetup) =
            TimelapseConfig(
                intervalMs = setup.intervalMs,
                stop = setup.stop,
                frameCount = setup.frameCount.takeIf { setup.stop == StopCondition.FRAME_COUNT },
                durationMs = setup.durationMs.takeIf { setup.stop == StopCondition.DURATION },
                lockExposure = setup.lockExposure,
                outputFps = setup.fps,
                adaptToHeat = setup.adaptToHeat,
            )

        /** The checklist, judged against what the phone is really doing right now. */
        fun preflight(
            setup: TimelapseSetup,
            settings: CaptureSettings,
            shutterNs: Long,
            overhead: OverheadEstimate,
            megapixels: Float,
        ): List<PreflightItem> {
            val config = config(setup)
            val status = deviceStatus.read()
            val frames = config.plannedFrames
            val bytes = IntervalMath.typicalFrameBytes(settings.format, megapixels)
            val duration = IntervalMath.sessionDurationMs(config)
            val alarms = context.getSystemService(AlarmManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            return Preflight.evaluate(
                PreflightInputs(
                    plannedFrames = frames,
                    intervalMs = setup.intervalMs,
                    sessionDurationMs = duration,
                    bytesPerFrame = bytes,
                    freeBytes = status.freeBytes,
                    batteryPercent = status.batteryPercent,
                    charging = status.charging,
                    estimatedBatteryPercent =
                        duration?.let {
                            IntervalMath.batteryPercent(it, setup.intervalMs, shutterNs, overhead.overheadMs)
                        },
                    airplaneMode =
                        runCatching {
                            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1
                        }.getOrNull(),
                    manualFocus = settings.focusMode == FocusMode.MANUAL,
                    exposureLocked = setup.lockExposure,
                    steady = steadiness.isSteady(),
                    intervalCheck = IntervalMath.check(setup.intervalMs, shutterNs, overhead),
                    notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    exactAlarmsAllowed = alarms.canUseExactAlarms(),
                    ignoringBatteryOptimisation = power.isIgnoringBatteryOptimizations(context.packageName),
                    screenKeptOn = setup.keepScreenOn,
                ),
            )
        }

        fun start(request: LaunchRequest) = TimelapseService.start(context, request)

        fun stop() = TimelapseService.stop(context)

        fun dismissFinished() = runState.dismissFinished()

        /** Sessions left RUNNING with nothing running: the process was killed or the phone shut down. */
        fun interrupted(): List<SessionSummary> {
            val running = (runState.state.value as? RunState.Running)?.sessionId?.takeIf { it.isNotEmpty() }
            return store.interrupted(running)
        }

        /** Builds the request that continues an interrupted session from its last good frame. */
        fun resumeRequest(
            sessionId: String,
            jpegOrientation: Int,
        ): LaunchRequest? {
            val session = store.open(sessionId) ?: return null
            val manifest = session.manifest
            val config = manifest.timelapse ?: return null
            val settings = manifest.requested.settings
            return LaunchRequest(
                lensKey = manifest.camera.lensKey,
                settings = settings,
                aspect = manifest.requested.aspect,
                config = config,
                lockedShutterNs = settings.shutterNs,
                lockedIso = settings.iso,
                jpegOrientation = jpegOrientation,
                keepScreenOn = true,
                resumeSessionId = sessionId,
                name = manifest.name,
                kind = manifest.kind,
            )
        }

        /**
         * The request for [count] dark frames at the exposure and ISO a finished session used. The lens has to be
         * covered by the person; Siderea only repeats the settings.
         */
        fun darkRequest(
            sessionId: String,
            count: Int,
            jpegOrientation: Int,
        ): LaunchRequest? {
            val manifest = store.open(sessionId)?.manifest ?: return null
            val settings = manifest.requested.settings
            return LaunchRequest(
                lensKey = manifest.camera.lensKey,
                settings = settings,
                aspect = manifest.requested.aspect,
                config =
                    manifest.timelapse
                        ?: TimelapseConfig(intervalMs = DARK_INTERVAL_MS, stop = StopCondition.FRAME_COUNT),
                lockedShutterNs = settings.shutterNs,
                lockedIso = settings.iso,
                jpegOrientation = jpegOrientation,
                keepScreenOn = false,
                name = manifest.name,
                kind = manifest.kind,
                darkFramesFor = sessionId,
                darkCount = count,
            )
        }

        /** Ends an interrupted session as it stands, keeping every captured frame. */
        suspend fun finalize(sessionId: String) =
            withContext(Dispatchers.IO) {
                store.open(sessionId)?.let {
                    it.addEvent("Finalised after an interruption, with the frames captured so far.")
                    it.finish(SessionStatus.STOPPED)
                }
            }

        fun launchRequest(
            lensKey: String,
            settings: CaptureSettings,
            aspect: String,
            setup: TimelapseSetup,
            shutterNs: Long,
            iso: Int,
            orientation: Int,
            kind: SessionKind = SessionKind.TIMELAPSE,
        ) = LaunchRequest(
            lensKey = lensKey,
            settings =
                settings.copy(
                    exposureMode = if (setup.lockExposure) ExposureMode.MANUAL else settings.exposureMode,
                ),
            aspect = aspect,
            config = config(setup),
            lockedShutterNs = shutterNs,
            lockedIso = iso,
            jpegOrientation = orientation,
            keepScreenOn = setup.keepScreenOn,
            kind = kind,
        )

        private companion object {
            const val DARK_INTERVAL_MS = 1_000L
            const val STEADY_DEGREES = 0.4f
            const val PROBE_FRAMES = 3
            const val NS_PER_MS = 1_000_000L
            const val MARGIN = 1.15
        }
    }
