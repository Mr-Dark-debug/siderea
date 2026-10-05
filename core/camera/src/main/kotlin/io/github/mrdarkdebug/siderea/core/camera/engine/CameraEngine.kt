package io.github.mrdarkdebug.siderea.core.camera.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Surface
import androidx.core.content.ContextCompat
import io.github.mrdarkdebug.siderea.core.camera.capability.EnumNames
import io.github.mrdarkdebug.siderea.core.camera.capability.SizeInfo
import io.github.mrdarkdebug.siderea.core.camera.control.OrientationMath
import io.github.mrdarkdebug.siderea.core.camera.control.SensorCalibration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Camera2 capture engine for still photography.
 *
 * Everything camera-related runs on one dedicated `HandlerThread`; the main thread only ever calls the
 * suspend functions and reads the flows. One engine owns one open camera at a time.
 *
 * The engine knows nothing about UI. A viewfinder supplies a `Surface` through [OpenParams.surfaceFor];
 * settings arrive as an already-planned [RequestPlan].
 */
class CameraEngine(
    private val context: Context,
) {
    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("siderea-camera", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val lifecycleLock = Mutex()
    private val captureLock = Mutex()

    private val mutableState = MutableStateFlow<EngineState>(EngineState.Closed)
    val state: StateFlow<EngineState> = mutableState.asStateFlow()

    private val mutableFrames = MutableStateFlow<FrameInfo?>(null)
    val frames: StateFlow<FrameInfo?> = mutableFrames.asStateFlow()

    private val mutableEvents = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<CaptureEvent> = mutableEvents.asSharedFlow()

    @Volatile private var active: Active? = null

    @Volatile private var pending: Pending? = null

    @Volatile private var previewPlan: RequestPlan? = null

    @Volatile private var focusRegion: MeteringRectangle? = null

    @Volatile private var observedNear: Float? = null

    // region lifecycle

    /** Opens [OpenParams.lens] and starts the preview. Any camera already open is closed first. */
    suspend fun open(params: OpenParams) {
        lifecycleLock.withLock {
            closeInternal()
            mutableState.value = EngineState.Opening
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                mutableState.value = EngineState.Failed("Siderea needs permission to use the camera.")
                return
            }
            var device: CameraDevice? = null
            try {
                device = openDevice(params.lens.openId)
                val wantsPhysical = params.lens.physicalId != null
                val configured =
                    try {
                        configure(device, params, usePhysical = wantsPhysical, note = null)
                    } catch (e: EngineException) {
                        if (!wantsPhysical) throw e
                        configure(
                            device,
                            params,
                            usePhysical = false,
                            note =
                                "This phone wouldn't open the ${params.lens.zoomLabel} lens on its own, so Siderea " +
                                    "zooms the main camera instead. RAW then comes from the main sensor.",
                        )
                    }
                active = configured
                startPreview(configured)
                mutableState.value = EngineState.Ready(configured.readyInfo)
            } catch (e: EngineException) {
                device?.close()
                mutableState.value = EngineState.Failed(e.userMessage)
            } catch (e: CameraAccessException) {
                device?.close()
                mutableState.value = EngineState.Failed(describeAccess(e))
            } catch (e: SecurityException) {
                device?.close()
                mutableState.value = EngineState.Failed("Android blocked camera access (${e.message}).")
            }
        }
    }

    suspend fun close() {
        lifecycleLock.withLock { closeInternal() }
    }

    /** Stops the camera thread for good. The engine can't be used afterwards. */
    fun release() {
        runCatching { active?.device?.close() }
        active = null
        thread.quitSafely()
    }

    private fun closeInternal() {
        val current = active
        active = null
        pending?.fail(EngineException("The camera was closed."))
        pending = null
        if (current != null) {
            runCatching { current.session.close() }
            runCatching { current.device.close() }
            runCatching { current.jpegReader?.close() }
            runCatching { current.rawReader?.close() }
        }
        mutableFrames.value = null
        focusRegion = null
        mutableState.value = EngineState.Closed
    }

    // endregion

    // region opening

    @SuppressLint("MissingPermission")
    private suspend fun openDevice(id: String): CameraDevice =
        suspendCancellableCoroutine { cont ->
            val callback =
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (cont.isActive) cont.resume(camera) else camera.close()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (cont.isActive) {
                            cont.resumeWithException(EngineException(MESSAGE_TAKEN))
                        } else {
                            onLost(MESSAGE_TAKEN)
                        }
                    }

                    override fun onError(
                        camera: CameraDevice,
                        error: Int,
                    ) {
                        camera.close()
                        val message = describeDeviceError(error)
                        if (cont.isActive) cont.resumeWithException(EngineException(message)) else onLost(message)
                    }
                }
            try {
                manager.openCamera(id, executor, callback)
            } catch (e: CameraAccessException) {
                cont.resumeWithException(EngineException(describeAccess(e), e))
            } catch (e: IllegalArgumentException) {
                cont.resumeWithException(EngineException("This camera isn't available (${e.message}).", e))
            }
        }

    private fun onLost(message: String) {
        scope.launch {
            lifecycleLock.withLock {
                closeInternal()
                mutableState.value = EngineState.Failed(message)
            }
        }
    }

    // One linear setup sequence; splitting it up would hide the order the camera requires.
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
    private suspend fun configure(
        device: CameraDevice,
        params: OpenParams,
        usePhysical: Boolean,
        note: String?,
    ): Active {
        val lens = params.lens
        val physicalId = if (usePhysical) lens.physicalId else null
        val chars = manager.getCameraCharacteristics(lens.openId)
        val lensChars = if (physicalId != null) manager.getCameraCharacteristics(physicalId) else chars
        val map =
            lensChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw EngineException("This camera doesn't list any output sizes.")

        val previewSizes =
            map
                .getOutputSizes(
                    SurfaceTexture::class.java,
                ).orEmpty()
                .map { SizeInfo(it.width, it.height) }
        val previewSize =
            SensorSizing.preview(previewSizes, params.aspect)
                ?: throw EngineException("This camera offers no preview size.")
        val previewSurface =
            params.surfaceFor(previewSize.width, previewSize.height)
                ?: throw EngineException("The viewfinder isn't ready yet. Try again in a moment.")

        fun sizes(format: Int): List<SizeInfo> =
            buildSet {
                map.getOutputSizes(format)?.forEach { add(SizeInfo(it.width, it.height)) }
                map.getHighResolutionOutputSizes(format)?.forEach { add(SizeInfo(it.width, it.height)) }
            }.toList()

        var jpegReader: ImageReader? = null
        var rawReader: ImageReader? = null
        try {
            val jpegSize =
                if (params.format.wantsJpeg) {
                    SensorSizing.capture(
                        sizes(ImageFormat.JPEG),
                        params.aspect,
                    )
                } else {
                    null
                }
            val canRaw = "RAW" in lens.info.capabilities
            val rawSize =
                if (params.format.wantsRaw && canRaw) {
                    SensorSizing.capture(sizes(ImageFormat.RAW_SENSOR), params.aspect)
                } else {
                    null
                }
            if (jpegSize == null && rawSize == null) {
                throw EngineException("This lens can't produce the chosen photo format. Pick JPEG instead.")
            }
            jpegReader = jpegSize?.let { ImageReader.newInstance(it.width, it.height, ImageFormat.JPEG, IMAGE_BUFFERS) }
            rawReader =
                rawSize?.let { ImageReader.newInstance(it.width, it.height, ImageFormat.RAW_SENSOR, IMAGE_BUFFERS) }
            jpegReader?.setOnImageAvailableListener({ onImage(it, isRaw = false) }, handler)
            rawReader?.setOnImageAvailableListener({ onImage(it, isRaw = true) }, handler)

            fun config(surface: Surface) =
                OutputConfiguration(surface).also { c ->
                    if (physicalId != null) c.setPhysicalCameraId(physicalId)
                }

            val outputs =
                buildList {
                    add(config(previewSurface))
                    jpegReader?.let { add(config(it.surface)) }
                    rawReader?.let { add(config(it.surface)) }
                }
            val session = createSession(device, outputs)
            val capabilities =
                EngineCapabilities(
                    afModes =
                        lensChars
                            .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                            .orEmptyInts()
                            .map(EnumNames::afMode)
                            .toSet(),
                    awbModes =
                        lensChars
                            .get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                            .orEmptyInts()
                            .map(EnumNames::awbMode)
                            .toSet(),
                    cct = lens.info.android16.cctSupported && lens.info.android16.colorTintKeyAvailable,
                    calibration = readCalibration(lensChars),
                )
            val noteForRaw =
                if (params.format.wantsRaw && rawSize == null) {
                    "This lens has no RAW output, so Siderea saves JPEG only."
                } else {
                    null
                }
            return Active(
                lens = lens,
                device = device,
                session = session,
                lensChars = lensChars,
                applier = RequestApplier(chars, physicalId),
                previewSurface = previewSurface,
                previewSize = SizeWH(previewSize.width, previewSize.height),
                jpegReader = jpegReader,
                rawReader = rawReader,
                // A logical camera that had to stand in for a physical lens is zoomed to look like it.
                zoomRatio = if (usePhysical || lens.physicalId == null) null else lens.zoomRatio,
                readyInfo =
                    ReadyInfo(
                        lens = lens,
                        previewSize = SizeWH(previewSize.width, previewSize.height),
                        jpegSize = jpegSize?.let { SizeWH(it.width, it.height) },
                        rawSize = rawSize?.let { SizeWH(it.width, it.height) },
                        usingLogicalFallback = lens.physicalId != null && !usePhysical,
                        note = note ?: noteForRaw,
                        capabilities = capabilities,
                    ),
                lensIsPhysical = physicalId != null,
            )
        } catch (e: EngineException) {
            jpegReader?.close()
            rawReader?.close()
            throw e
        }
    }

    private suspend fun createSession(
        device: CameraDevice,
        outputs: List<OutputConfiguration>,
    ): CameraCaptureSession =
        suspendCancellableCoroutine { cont ->
            val callback =
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cont.isActive) cont.resume(session) else session.close()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        if (cont.isActive) {
                            cont.resumeWithException(EngineException("The camera couldn't start with these settings."))
                        }
                    }
                }
            val config = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, callback)
            val supported =
                try {
                    device.isSessionConfigurationSupported(config)
                } catch (_: UnsupportedOperationException) {
                    true // the camera can't pre-check; let createCaptureSession decide
                } catch (_: IllegalArgumentException) {
                    false
                }
            if (!supported) {
                cont.resumeWithException(EngineException("This combination of streams isn't supported."))
                return@suspendCancellableCoroutine
            }
            try {
                device.createCaptureSession(config)
            } catch (e: CameraAccessException) {
                cont.resumeWithException(EngineException(describeAccess(e), e))
            } catch (e: IllegalArgumentException) {
                cont.resumeWithException(EngineException("The camera rejected the stream setup (${e.message}).", e))
            }
        }

    private fun readCalibration(chars: CameraCharacteristics): SensorCalibration? {
        val t1 = chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1) ?: return null
        val i1 = chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1) ?: return null
        val t2 = chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)
        val i2 = chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt()
        return SensorCalibration(i1, matrixOf(t1), i2, t2?.let(::matrixOf))
    }

    private fun matrixOf(t: ColorSpaceTransform): DoubleArray =
        DoubleArray(MATRIX_SIZE) { i -> t.getElement(i % MATRIX_DIM, i / MATRIX_DIM).toDouble() }

    // endregion

    // region preview

    /** Replaces the live-preview settings. Cheap; call it as often as the user moves a control. */
    fun updatePreviewPlan(plan: RequestPlan) {
        previewPlan = plan
        handler.post { restartRepeating() }
    }

    private fun startPreview(active: Active) {
        observedNear = null
        restartRepeating(active)
    }

    private fun restartRepeating(target: Active? = active) {
        val current = target ?: return
        val plan = previewPlan ?: return
        if (pending?.suspendsPreview == true) return
        try {
            val builder = current.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(current.previewSurface)
            current.applier.apply(builder, plan, current.zoomRatio)
            focusRegion?.let { region ->
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
            }
            current.session.setRepeatingRequest(builder.build(), previewCallback, handler)
        } catch (e: CameraAccessException) {
            onLost(describeAccess(e))
        } catch (e: IllegalStateException) {
            // The session closed while we were updating it; a close is already in progress.
            if (active === current) onLost("The camera stopped unexpectedly (${e.message}). Try again.")
        }
    }

    private val previewCallback =
        object : CameraCaptureSession.CaptureCallback() {
            private var frame = 0

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                val near = result.get(CaptureResult.LENS_FOCUS_RANGE)?.first
                if (near != null && near > 0f) observedNear = maxOf(observedNear ?: 0f, near)
                if (++frame % FRAMES_PER_UPDATE != 0) return
                mutableFrames.value =
                    FrameInfo(
                        exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                        iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
                        focusDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                        afState = result.get(CaptureResult.CONTROL_AF_STATE),
                        aeState = result.get(CaptureResult.CONTROL_AE_STATE),
                        observedNearDiopters = observedNear,
                    )
            }
        }

    /** Focuses (and meters) on a point given as fractions of the upright preview. */
    fun focusAt(
        fractionX: Float,
        fractionY: Float,
    ) {
        handler.post {
            val current = active ?: return@post
            val plan = previewPlan ?: return@post
            if (plan.afMode == "OFF") return@post
            val array: Rect =
                current.lensChars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?: return@post
            val (sx, sy) = SensorSizing.tapToSensor(fractionX, fractionY, current.lens.sensorOrientation)
            val half = (maxOf(array.width(), array.height()) * FOCUS_REGION_FRACTION / 2).toInt()
            val cx = array.left + (sx * array.width()).toInt()
            val cy = array.top + (sy * array.height()).toInt()
            val left = (cx - half).coerceIn(array.left, array.right - 2 * half)
            val top = (cy - half).coerceIn(array.top, array.bottom - 2 * half)
            val region = MeteringRectangle(left, top, 2 * half, 2 * half, MeteringRectangle.METERING_WEIGHT_MAX)
            focusRegion = region
            try {
                val builder = current.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(current.previewSurface)
                current.applier.apply(builder, plan, current.zoomRatio)
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
                builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                current.session.capture(builder.build(), null, handler)
            } catch (_: CameraAccessException) {
                // A tap that can't be honoured is not worth surfacing; the preview keeps running.
            } catch (_: IllegalStateException) {
                // Session closing.
            }
            restartRepeating(current)
        }
    }

    // endregion

    // region still capture

    /**
     * Takes one photo with [request]. Long manual exposures keep the preview paused until the sensor
     * finishes; progress is announced on [events].
     *
     * @throws EngineException with a human-readable message when the capture can't be completed.
     */
    suspend fun capture(request: StillRequest): CapturedPhoto =
        captureLock.withLock {
            val cam = active ?: throw EngineException("The camera isn't ready yet.")
            val wantsJpeg = request.format.wantsJpeg && cam.jpegReader != null
            val wantsRaw = request.format.wantsRaw && cam.rawReader != null
            if (!wantsJpeg && !wantsRaw) throw EngineException("This lens can't produce the chosen photo format.")

            drain(cam.jpegReader)
            drain(cam.rawReader)
            val stillPlan = request.plan
            val shutterMs = stillPlan.shutterNs / NS_PER_MS
            val longExposure = !request.plan.autoExposure && request.plan.shutterNs > LONG_EXPOSURE_NS
            val job = Pending(wantsJpeg, wantsRaw, suspendsPreview = longExposure)
            pending = job
            try {
                val builder = cam.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                if (!longExposure) builder.addTarget(cam.previewSurface)
                if (wantsJpeg) builder.addTarget(requireNotNull(cam.jpegReader).surface)
                if (wantsRaw) builder.addTarget(requireNotNull(cam.rawReader).surface)
                cam.applier.apply(builder, request.plan, cam.zoomRatio)
                builder.set(CaptureRequest.JPEG_ORIENTATION, request.jpegOrientation)
                builder.set(CaptureRequest.JPEG_QUALITY, request.jpegQuality.toByte())
                if (wantsRaw) builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, 1)
                if (longExposure) cam.session.stopRepeating()
                val callback =
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureStarted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            timestamp: Long,
                            frameNumber: Long,
                        ) {
                            mutableEvents.tryEmit(
                                CaptureEvent.Exposing(stillPlan.shutterNs, SystemClock.elapsedRealtime()),
                            )
                        }

                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult,
                        ) {
                            mutableEvents.tryEmit(CaptureEvent.Processing)
                            job.result = result
                            job.maybeFinish()
                        }

                        override fun onCaptureFailed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            failure: CaptureFailure,
                        ) {
                            job.fail(
                                EngineException("The camera dropped this photo (reason ${failure.reason}). Try again."),
                            )
                        }
                    }
                cam.session.capture(builder.build(), callback, handler)
                try {
                    withTimeout(shutterMs + CAPTURE_TIMEOUT_MARGIN_MS) { job.done.await() }
                } catch (e: TimeoutCancellationException) {
                    cam.session.abortCaptures()
                    throw EngineException("The camera didn't deliver the photo in time. Try a shorter exposure.", e)
                }
                finish(cam, job, request)
            } catch (e: CameraAccessException) {
                throw EngineException(describeAccess(e), e)
            } finally {
                job.discardUnused()
                pending = null
                if (active === cam) handler.post { restartRepeating(cam) }
            }
        }

    /** Stops a capture in progress, for example a long exposure the user no longer wants. */
    fun cancelCapture() {
        handler.post {
            pending?.fail(CancellationException("Capture cancelled"))
            runCatching { active?.session?.abortCaptures() }
        }
    }

    private suspend fun finish(
        cam: Active,
        job: Pending,
        request: StillRequest,
    ): CapturedPhoto {
        val result = checkNotNull(job.result)
        val facts =
            CaptureFacts(
                exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
                focusDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION),
                sensorTimestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP),
                colorTemperatureHint = null,
            )
        val raw = job.rawImage
        var dng: File? = null
        if (raw != null) {
            dng = withContext(Dispatchers.IO) { writeDng(cam, result, raw, request.jpegOrientation) }
        }
        val width = raw?.width ?: cam.readyInfo.jpegSize?.width ?: 0
        val height = raw?.height ?: cam.readyInfo.jpegSize?.height ?: 0
        return CapturedPhoto(job.jpeg, dng, width, height, facts)
    }

    private fun writeDng(
        cam: Active,
        result: TotalCaptureResult,
        image: Image,
        jpegOrientation: Int,
    ): File {
        val physical = cam.lens.physicalId?.takeIf { cam.lensIsPhysical }
        val source: CaptureResult = physical?.let { result.physicalCameraResults[it] } ?: result
        val file = File.createTempFile("siderea-", ".dng", context.cacheDir)
        try {
            DngCreator(cam.lensChars, source).use { dng ->
                dng.setOrientation(OrientationMath.exifOrientation(jpegOrientation))
                dng.setDescription("Siderea")
                file.outputStream().buffered().use { out -> dng.writeImage(out, image) }
            }
        } catch (e: java.io.IOException) {
            file.delete()
            throw EngineException("Couldn't write the RAW file. Is the phone's storage full?", e)
        } catch (e: IllegalArgumentException) {
            file.delete()
            throw EngineException("The camera's RAW data couldn't be converted to DNG (${e.message}).", e)
        }
        return file
    }

    private fun drain(reader: ImageReader?) {
        if (reader == null) return
        while (true) {
            val image = reader.acquireNextImage() ?: return
            image.close()
        }
    }

    private fun onImage(
        reader: ImageReader,
        isRaw: Boolean,
    ) {
        val image = reader.acquireNextImage() ?: return
        val job = pending
        if (job == null) {
            image.close()
            return
        }
        if (isRaw) {
            job.rawImage?.close()
            job.rawImage = image
        } else {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            image.close()
            job.jpeg = bytes
        }
        job.maybeFinish()
    }

    // endregion

    private class Active(
        val lens: Lens,
        val device: CameraDevice,
        val session: CameraCaptureSession,
        val lensChars: CameraCharacteristics,
        val applier: RequestApplier,
        val previewSurface: Surface,
        val previewSize: SizeWH,
        val jpegReader: ImageReader?,
        val rawReader: ImageReader?,
        val zoomRatio: Float?,
        val readyInfo: ReadyInfo,
        val lensIsPhysical: Boolean,
    )

    /** The capture in flight: collects the result and the image(s) until all have arrived. */
    private class Pending(
        val wantsJpeg: Boolean,
        val wantsRaw: Boolean,
        val suspendsPreview: Boolean,
    ) {
        val done = CompletableDeferred<Unit>()

        @Volatile var result: TotalCaptureResult? = null

        @Volatile var jpeg: ByteArray? = null

        @Volatile var rawImage: Image? = null

        @Synchronized
        fun maybeFinish() {
            val jpegReady = !wantsJpeg || jpeg != null
            val rawReady = !wantsRaw || rawImage != null
            if (result != null && jpegReady && rawReady) done.complete(Unit)
        }

        fun fail(error: Throwable) {
            done.completeExceptionally(error)
        }

        /** Releases the RAW image buffer; it is returned to the reader once the DNG is written or abandoned. */
        fun discardUnused() {
            runCatching { rawImage?.close() }
            rawImage = null
        }
    }

    private companion object {
        const val IMAGE_BUFFERS = 2
        const val EVENT_BUFFER = 16
        const val FRAMES_PER_UPDATE = 6
        const val NS_PER_MS = 1_000_000L
        const val LONG_EXPOSURE_NS = 500_000_000L
        const val CAPTURE_TIMEOUT_MARGIN_MS = 15_000L
        const val FOCUS_REGION_FRACTION = 0.12
        const val MATRIX_SIZE = 9
        const val MATRIX_DIM = 3
        const val MESSAGE_TAKEN =
            "The camera was taken by another app. Close the other camera app, then reopen Siderea."

        fun describeDeviceError(error: Int): String =
            when (error) {
                CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> {
                    "Another app is using this camera. Close it and try again."
                }

                CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> {
                    "Too many cameras are open on this phone. Close other camera apps and try again."
                }

                CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> {
                    "The camera is disabled by a device policy (for example a work profile)."
                }

                CameraDevice.StateCallback.ERROR_CAMERA_DEVICE, CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> {
                    "The camera hardware reported an error. Restarting the phone usually clears it."
                }

                else -> {
                    "The camera failed (error $error). Try again."
                }
            }

        fun describeAccess(e: CameraAccessException): String =
            when (e.reason) {
                CameraAccessException.CAMERA_DISABLED -> {
                    "The camera is disabled by a device policy."
                }

                CameraAccessException.CAMERA_DISCONNECTED -> {
                    MESSAGE_TAKEN
                }

                CameraAccessException.CAMERA_IN_USE -> {
                    "Another app is using this camera. Close it and try again."
                }

                CameraAccessException.MAX_CAMERAS_IN_USE -> {
                    "Too many cameras are open on this phone. Close other camera apps and try again."
                }

                else -> {
                    "The camera service reported a problem (${e.message}). Try again."
                }
            }

        fun IntArray?.orEmptyInts(): IntArray = this ?: IntArray(0)
    }
}
