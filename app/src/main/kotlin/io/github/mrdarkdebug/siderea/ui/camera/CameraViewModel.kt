package io.github.mrdarkdebug.siderea.ui.camera

import android.graphics.SurfaceTexture
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mrdarkdebug.siderea.core.camera.analysis.FrameAnalysis
import io.github.mrdarkdebug.siderea.core.camera.analysis.LumaAnalysis
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityRepository
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityState
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.camera.control.OrientationMath
import io.github.mrdarkdebug.siderea.core.camera.control.SoftwareAe
import io.github.mrdarkdebug.siderea.core.camera.control.WhiteBalance
import io.github.mrdarkdebug.siderea.core.camera.engine.AspectRatio
import io.github.mrdarkdebug.siderea.core.camera.engine.CameraEngine
import io.github.mrdarkdebug.siderea.core.camera.engine.CaptureEvent
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineCapabilities
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineException
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineState
import io.github.mrdarkdebug.siderea.core.camera.engine.Lens
import io.github.mrdarkdebug.siderea.core.camera.engine.LensCatalog
import io.github.mrdarkdebug.siderea.core.camera.engine.OpenParams
import io.github.mrdarkdebug.siderea.core.camera.engine.RequestPlanner
import io.github.mrdarkdebug.siderea.core.camera.engine.StillRequest
import io.github.mrdarkdebug.siderea.core.data.settings.CameraStateRepository
import io.github.mrdarkdebug.siderea.device.DeviceMotion
import io.github.mrdarkdebug.siderea.device.DeviceStatusReader
import io.github.mrdarkdebug.siderea.device.PhotoSaver
import io.github.mrdarkdebug.siderea.device.SaveException
import io.github.mrdarkdebug.siderea.device.ShutterKeyBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.pow

/**
 * Owns the camera screen's state. All camera work happens in [CameraEngine] on its own thread; this class
 * decides what to ask of it and turns what it reports into [CameraUiState].
 */
@HiltViewModel
class CameraViewModel
    @Inject
    constructor(
        private val capabilities: CapabilityRepository,
        private val engine: CameraEngine,
        private val prefsStore: CameraStateRepository,
        private val saver: PhotoSaver,
        private val deviceStatus: DeviceStatusReader,
        private val motion: DeviceMotion,
        private val keys: ShutterKeyBus,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(CameraUiState())
        val state: StateFlow<CameraUiState> = mutableState.asStateFlow()

        private val mutableAnalysis = MutableStateFlow<FrameAnalysis?>(null)

        /** Live histogram and focus/zebra masks. Separate from [state] so only the overlays redraw. */
        val analysis: StateFlow<FrameAnalysis?> = mutableAnalysis.asStateFlow()

        val motionState = motion.state

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        private val sdk = Build.VERSION.SDK_INT

        private var surfaceTexture: SurfaceTexture? = null
        private var previewSurface: Surface? = null
        private var foreground = false
        private var capabilitiesAccepted = false
        private var engineCapabilities: EngineCapabilities = EngineCapabilities.NONE
        private var openJob: Job? = null
        private var captureJob: Job? = null
        private var analysisJob: Job? = null
        private var persistJob: Job? = null
        private var lastAeStepAt = 0L
        private var aeShutterNs = CaptureSettings.DEFAULT_SHUTTER_NS
        private var aeIso = CaptureSettings.DEFAULT_ISO
        private var nextMessageId = 0L
        private var lastOpenKey: String? = null

        init {
            viewModelScope.launch { capabilities.ensureLoaded() }
            viewModelScope.launch { capabilities.state.collect(::onCapabilities) }
            viewModelScope.launch { engine.state.collect(::onEngineState) }
            viewModelScope.launch { engine.frames.collect { frame -> mutableState.update { it.copy(live = frame) } } }
            viewModelScope.launch { engine.events.collect(::onCaptureEvent) }
            viewModelScope.launch { keys.presses.collect { onShutterPressed() } }
            viewModelScope.launch {
                while (true) {
                    mutableState.update { it.copy(device = deviceStatus.read()) }
                    delay(STATUS_INTERVAL_MS)
                }
            }
        }

        // region lifecycle and surfaces

        fun onPermissionResult(granted: Boolean) {
            mutableState.update {
                it.copy(
                    permission = if (granted) PermissionState.GRANTED else PermissionState.DENIED,
                )
            }
            if (granted) maybeOpen()
        }

        fun onForeground(isForeground: Boolean) {
            foreground = isForeground
            if (isForeground) {
                motion.start()
                maybeOpen()
            } else {
                motion.stop()
                captureJob?.cancel()
                engine.cancelCapture()
                openJob?.cancel()
                lastOpenKey = null
                viewModelScope.launch { engine.close() }
            }
        }

        fun onViewfinderAvailable(texture: SurfaceTexture) {
            surfaceTexture = texture
            maybeOpen()
        }

        fun onViewfinderDestroyed() {
            val texture = surfaceTexture
            surfaceTexture = null
            lastOpenKey = null
            viewModelScope.launch {
                // The camera must stop writing before its surface is released.
                engine.close()
                previewSurface?.release()
                previewSurface = null
                texture?.release()
            }
        }

        private fun maybeOpen(force: Boolean = false) {
            val s = mutableState.value
            val lens = s.lens ?: return
            if (s.permission != PermissionState.GRANTED || !foreground) return
            val texture = surfaceTexture ?: return
            val key = "${lens.key}|${s.settings.format}|${s.aspect}"
            if (!force && key == lastOpenKey && s.engine !is EngineState.Failed) return
            lastOpenKey = key
            openJob?.cancel()
            openJob =
                viewModelScope.launch {
                    engine.open(
                        OpenParams(lens, s.settings.format, s.aspect) { width, height ->
                            previewSurface?.release()
                            texture.setDefaultBufferSize(width, height)
                            Surface(texture).also { previewSurface = it }
                        },
                    )
                }
        }

        fun retryOpen() {
            lastOpenKey = null
            maybeOpen(force = true)
        }

        // endregion

        // region engine and capability state

        private suspend fun onCapabilities(state: CapabilityState) {
            when (state) {
                CapabilityState.Loading -> {
                    Unit
                }

                is CapabilityState.Failed -> {
                    mutableState.update { it.copy(capabilityError = state.message) }
                }

                is CapabilityState.Ready -> {
                    if (capabilitiesAccepted) return
                    capabilitiesAccepted = true
                    val lenses = LensCatalog.from(state.report)
                    val saved =
                        runCatching {
                            prefsStore.json.first()?.let { json.decodeFromString(CameraPrefs.serializer(), it) }
                        }.getOrNull() ?: CameraPrefs()
                    val lens = lenses.firstOrNull { it.key == saved.lensKey } ?: LensCatalog.default(lenses)
                    val limits = lens?.let(::limitsFor)
                    val settings =
                        if (limits !=
                            null
                        ) {
                            CameraSettingsOps.coerce(saved.settings, limits)
                        } else {
                            saved.settings
                        }
                    aeShutterNs = settings.shutterNs
                    aeIso = settings.iso
                    mutableState.update {
                        it.copy(
                            capabilitiesLoaded = true,
                            lenses = lenses,
                            lens = lens,
                            limits = limits,
                            settings = settings,
                            aspect =
                                AspectRatio.entries.firstOrNull { a -> a.name == saved.aspect }
                                    ?: AspectRatio.FOUR_THREE,
                            aids = saved.aids,
                            timerSeconds = saved.timerSeconds,
                            effectiveShutterNs = settings.shutterNs,
                            effectiveIso = settings.iso,
                        )
                    }
                    if (lens == null) {
                        mutableState.update {
                            it.copy(
                                capabilityError =
                                    "This phone reports no usable camera. " +
                                        "Open the Capability Inspector for details.",
                            )
                        }
                    }
                    maybeOpen()
                }
            }
        }

        private fun onEngineState(engineState: EngineState) {
            mutableState.update { it.copy(engine = engineState, ready = (engineState as? EngineState.Ready)?.info) }
            if (engineState is EngineState.Ready) {
                engineCapabilities = engineState.info.capabilities
                engineState.info.note?.let(::showMessage)
                pushPreviewPlan()
            }
        }

        private fun limitsFor(lens: Lens): ExposureLimits = ExposureLimits.from(lens.info, sdk)

        // endregion

        // region controls

        fun selectLens(key: String) {
            val s = mutableState.value
            val lens = s.lenses.firstOrNull { it.key == key } ?: return
            if (lens.key == s.lens?.key || s.isCapturing) return
            val limits = limitsFor(lens)
            val settings = CameraSettingsOps.coerce(s.settings, limits)
            mutableState.update {
                it.copy(
                    lens = lens,
                    limits = limits,
                    settings = settings,
                    panel = null,
                    live = null,
                )
            }
            showCoercionNotes(s.settings, settings, lens)
            seedAe()
            schedulePersist()
            maybeOpen()
        }

        fun flipFacing() {
            val s = mutableState.value
            val current = s.lens ?: return
            val target =
                s.lenses.firstOrNull { it.facing != current.facing && it.zoomLabel == "1x" }
                    ?: s.lenses.firstOrNull { it.facing != current.facing }
                    ?: return
            selectLens(target.key)
        }

        fun cycleFormat() {
            val s = mutableState.value
            val next = CameraSettingsOps.nextFormat(s.settings.format, s.limits?.raw == true)
            if (next == s.settings.format) {
                showMessage(s.limits?.rawReason ?: "This lens can only shoot JPEG.")
                return
            }
            update { it.copy(format = next) }
            maybeOpen(force = true)
        }

        fun cycleAspect() {
            if (mutableState.value.isCapturing) return
            mutableState.update {
                it.copy(
                    aspect =
                        if (it.aspect ==
                            AspectRatio.FOUR_THREE
                        ) {
                            AspectRatio.SIXTEEN_NINE
                        } else {
                            AspectRatio.FOUR_THREE
                        },
                )
            }
            schedulePersist()
            maybeOpen(force = true)
        }

        fun cycleTimer() {
            mutableState.update { it.copy(timerSeconds = CameraSettingsOps.nextTimer(it.timerSeconds)) }
            schedulePersist()
        }

        fun setAids(transform: (Aids) -> Aids) {
            mutableState.update { it.copy(aids = transform(it.aids)) }
            schedulePersist()
        }

        fun togglePanel(panel: ControlPanel) {
            mutableState.update { it.copy(panel = if (it.panel == panel) null else panel) }
        }

        fun closePanel() {
            mutableState.update { it.copy(panel = null) }
        }

        fun setShutterManual(manual: Boolean) {
            val s = mutableState.value
            if (manual && s.limits?.manualExposure != true) return showMessage(s.limits?.manualExposureReason.orEmpty())
            seedAe()
            update { CameraSettingsOps.withShutterManual(it, manual) }
        }

        fun setIsoManual(manual: Boolean) {
            val s = mutableState.value
            if (manual && s.limits?.manualExposure != true) return showMessage(s.limits?.manualExposureReason.orEmpty())
            seedAe()
            update { CameraSettingsOps.withIsoManual(it, manual) }
        }

        fun setShutter(ns: Long) = update { it.copy(shutterNs = ns) }

        fun setIso(iso: Int) = update { it.copy(iso = iso) }

        fun setEv(stops: Float) = update { it.copy(evStops = stops) }

        fun setFocusManual(manual: Boolean) {
            val s = mutableState.value
            if (manual && s.limits?.manualFocus != true) return showMessage(s.limits?.manualFocusReason.orEmpty())
            update { it.copy(focusMode = if (manual) FocusMode.MANUAL else FocusMode.AUTO) }
        }

        fun setFocus(diopters: Float) = update { it.copy(focusDiopters = diopters) }

        fun setWhiteBalance(wb: WhiteBalance) = update { it.copy(whiteBalance = wb) }

        fun tapToFocus(
            fractionX: Float,
            fractionY: Float,
        ) {
            if (mutableState.value.settings.focusMode == FocusMode.AUTO) engine.focusAt(fractionX, fractionY)
        }

        fun selectMode(mode: CameraMode) {
            if (mode.availableSince != null) {
                showMessage(
                    "${mode.label.lowercase().replaceFirstChar {
                        it.uppercase()
                    }} isn't built yet. It arrives in ${mode.availableSince}.",
                )
                return
            }
            mutableState.update { it.copy(mode = mode) }
        }

        fun dismissMessage() {
            mutableState.update { it.copy(message = null) }
        }

        /** Applies an edit to the settings, keeps them inside the lens's limits, and tells the camera. */
        private fun update(edit: (CaptureSettings) -> CaptureSettings) {
            val limits = mutableState.value.limits
            mutableState.update { s ->
                val edited = edit(s.settings)
                s.copy(settings = if (limits != null) CameraSettingsOps.coerce(edited, limits) else edited)
            }
            recomputeEffective()
            pushPreviewPlan()
            schedulePersist()
        }

        private fun seedAe() {
            val s = mutableState.value
            aeShutterNs = s.live?.exposureNs ?: s.settings.shutterNs
            aeIso = s.live?.iso ?: s.settings.iso
            recomputeEffective()
        }

        private fun recomputeEffective() {
            mutableState.update { s ->
                val settings = s.settings
                val (shutter, iso) =
                    when (settings.exposureMode) {
                        ExposureMode.MANUAL -> settings.shutterNs to settings.iso
                        ExposureMode.SHUTTER_PRIORITY -> settings.shutterNs to aeIso
                        ExposureMode.ISO_PRIORITY -> aeShutterNs to settings.iso
                        ExposureMode.AUTO -> (s.live?.exposureNs ?: settings.shutterNs) to (s.live?.iso ?: settings.iso)
                    }
                s.copy(effectiveShutterNs = shutter, effectiveIso = iso)
            }
        }

        private fun pushPreviewPlan() {
            val s = mutableState.value
            val limits = s.limits ?: return
            if (s.engine !is EngineState.Ready) return
            val plan =
                RequestPlanner.plan(
                    s.settings,
                    s.effectiveShutterNs,
                    s.effectiveIso,
                    limits,
                    engineCapabilities,
                    forPreview = true,
                )
            engine.updatePreviewPlan(plan)
            if (plan.displayGain != s.displayGain) mutableState.update { it.copy(displayGain = plan.displayGain) }
        }

        private fun showCoercionNotes(
            before: CaptureSettings,
            after: CaptureSettings,
            lens: Lens,
        ) {
            val notes =
                buildList {
                    if (before.focusMode == FocusMode.MANUAL && after.focusMode == FocusMode.AUTO) {
                        add("The ${lens.zoomLabel} lens is fixed-focus, so focus is automatic.")
                    }
                    if (before.format.wantsRaw &&
                        !after.format.wantsRaw
                    ) {
                        add("The ${lens.zoomLabel} lens has no RAW, so photos are JPEG.")
                    }
                    if (before.exposureMode != ExposureMode.AUTO && after.exposureMode == ExposureMode.AUTO) {
                        add("The ${lens.zoomLabel} lens has no manual exposure, so exposure is automatic.")
                    }
                }
            notes.firstOrNull()?.let(::showMessage)
        }

        // endregion

        // region analysis and software auto exposure

        /** Called by the viewfinder a few times a second with a small copy of what is on screen. */
        fun onPreviewPixels(
            pixels: IntArray,
            width: Int,
            height: Int,
        ) {
            if (analysisJob?.isActive == true) return
            val aids = mutableState.value.aids
            analysisJob =
                viewModelScope.launch(Dispatchers.Default) {
                    val result =
                        LumaAnalysis.analyze(
                            pixels,
                            width,
                            height,
                            peakingThreshold = if (aids.peaking) LumaAnalysis.DEFAULT_PEAKING_THRESHOLD else null,
                            zebraThreshold = if (aids.zebra) LumaAnalysis.ZEBRA_THRESHOLD else null,
                        )
                    mutableAnalysis.value = result
                    runSoftwareAe(result.meanLuma)
                }
        }

        private fun runSoftwareAe(meanLuma: Float) {
            val s = mutableState.value
            val limits = s.limits ?: return
            val priority = s.settings.exposureMode
            if (priority != ExposureMode.SHUTTER_PRIORITY && priority != ExposureMode.ISO_PRIORITY) return
            if (s.engine !is EngineState.Ready || s.isCapturing) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastAeStepAt < AE_INTERVAL_MS) return
            // The viewfinder may be brightened for display only; judge the exposure by what the sensor really gave.
            val luma =
                meanLuma *
                    s.displayGain
                        .toDouble()
                        .pow(1 / GAMMA)
                        .toFloat()
            val step =
                SoftwareAe.step(
                    meanLuma = luma,
                    shutterNs = s.effectiveShutterNs,
                    iso = s.effectiveIso,
                    shutterPriority = priority == ExposureMode.SHUTTER_PRIORITY,
                    limits = limits,
                    bias = s.settings.evStops,
                )
            lastAeStepAt = now
            mutableState.update { it.copy(aeLimited = step.limited) }
            if (step.shutterNs != s.effectiveShutterNs || step.iso != s.effectiveIso) {
                aeShutterNs = step.shutterNs
                aeIso = step.iso
                recomputeEffective()
                pushPreviewPlan()
            }
        }

        // endregion

        // region capture

        fun onShutterPressed() {
            when (mutableState.value.capture) {
                CaptureUi.Idle -> {
                    startCapture()
                }

                is CaptureUi.Countdown -> {
                    captureJob?.cancel()
                    mutableState.update { it.copy(capture = CaptureUi.Idle) }
                }

                is CaptureUi.Exposing -> {
                    engine.cancelCapture()
                }

                CaptureUi.Saving -> {
                    Unit
                }
            }
        }

        private fun startCapture() {
            val s = mutableState.value
            if (s.engine !is EngineState.Ready) {
                showMessage("The camera isn't ready yet.")
                return
            }
            if (s.mode != CameraMode.PHOTO) return
            captureJob =
                viewModelScope.launch {
                    try {
                        for (left in s.timerSeconds downTo 1) {
                            mutableState.update { it.copy(capture = CaptureUi.Countdown(left)) }
                            delay(ONE_SECOND_MS)
                        }
                        takePhoto()
                    } catch (_: CancellationException) {
                        mutableState.update { it.copy(capture = CaptureUi.Idle) }
                    }
                }
        }

        private suspend fun takePhoto() {
            val s = mutableState.value
            val limits = s.limits ?: return
            val lens = s.lens ?: return
            val plan =
                RequestPlanner.plan(
                    s.settings,
                    s.effectiveShutterNs,
                    s.effectiveIso,
                    limits,
                    engineCapabilities,
                    forPreview = false,
                )
            val orientation =
                OrientationMath.jpegOrientation(
                    lens.sensorOrientation,
                    motion.state.value.deviceOrientation,
                    lens.isFront,
                )
            mutableState.update { it.copy(capture = CaptureUi.Exposing(plan.shutterNs, startedAtElapsedMs = null)) }
            try {
                val photo = engine.capture(StillRequest(plan, s.settings.format, orientation))
                mutableState.update { it.copy(capture = CaptureUi.Saving) }
                val saved = saver.save(photo)
                mutableState.update { it.copy(lastPhotoUri = saved.primary.toString()) }
            } catch (e: EngineException) {
                showMessage(e.userMessage)
            } catch (e: SaveException) {
                showMessage(e.userMessage)
            } catch (_: CancellationException) {
                // The user stopped the exposure.
            } finally {
                mutableState.update { it.copy(capture = CaptureUi.Idle) }
            }
        }

        private fun onCaptureEvent(event: CaptureEvent) {
            if (event is CaptureEvent.Exposing) {
                mutableState.update { s ->
                    if (s.capture is CaptureUi.Exposing) {
                        s.copy(capture = CaptureUi.Exposing(event.durationNs, event.startedAtElapsedMs))
                    } else {
                        s
                    }
                }
            }
        }

        // endregion

        suspend fun thumbnail(uri: String): android.graphics.Bitmap? = saver.thumbnail(android.net.Uri.parse(uri))

        private fun schedulePersist() {
            persistJob?.cancel()
            persistJob =
                viewModelScope.launch {
                    delay(PERSIST_DELAY_MS)
                    val s = mutableState.value
                    val prefs = CameraPrefs(s.lens?.key, s.settings, s.aspect.name, s.aids, s.timerSeconds)
                    prefsStore.save(json.encodeToString(CameraPrefs.serializer(), prefs))
                }
        }

        private fun showMessage(text: String) {
            if (text.isBlank()) return
            mutableState.update { it.copy(message = UiMessage(nextMessageId++, text)) }
        }

        override fun onCleared() {
            motion.stop()
            super.onCleared()
        }

        private companion object {
            const val STATUS_INTERVAL_MS = 20_000L
            const val AE_INTERVAL_MS = 350L
            const val ONE_SECOND_MS = 1_000L
            const val PERSIST_DELAY_MS = 600L
            const val GAMMA = 2.2
        }
    }
