package io.github.mrdarkdebug.siderea.core.camera.capability

/** Synthetic cameras for tests. Deliberately not modelled on any specific phone. */
internal object Samples {
    const val SECOND_NS = 1_000_000_000L

    /** A camera that supports everything Siderea can use. */
    fun proCamera(id: String = "0") =
        CameraInfo(
            id = id,
            kind = CameraKind.LOGICAL,
            physicalIds = listOf("2", "3"),
            facing = LensFacing.BACK,
            hardwareLevel = "LEVEL_3",
            capabilities = listOf("BACKWARD_COMPATIBLE", "MANUAL_SENSOR", "RAW", "MANUAL_POST_PROCESSING"),
            manualSensor = true,
            raw = true,
            lens =
                LensInfo(
                    focalLengthsMm = listOf(6.9f),
                    equivalentFocalLengthsMm = listOf(24f),
                    apertures = listOf(1.7f),
                ),
            sensor =
                SensorInfo(
                    physicalWidthMm = 9.8f,
                    physicalHeightMm = 7.3f,
                    pixelArrayWidth = 8160,
                    pixelArrayHeight = 6120,
                    megapixels = 49.9f,
                    blackLevelPattern = listOf(256, 256, 256, 256),
                    whiteLevel = 4095,
                ),
            exposure =
                ExposureInfo(
                    exposureTimeMinNs = 13_000L,
                    exposureTimeMaxNs = 32 * SECOND_NS,
                    isoMin = 50,
                    isoMax = 6400,
                    maxFrameDurationNs = 32 * SECOND_NS,
                    aeCompensationMin = -12,
                    aeCompensationMax = 12,
                    aeCompensationStep = 1.0 / 6.0,
                ),
            focus =
                FocusInfo(
                    minimumFocusDistanceDiopters = 10f,
                    hyperfocalDiopters = 0.4f,
                    calibration = "APPROXIMATE",
                ),
            outputs =
                OutputInfo(
                    rawSizes = listOf(SizeInfo(8160, 6120), SizeInfo(4080, 3060)),
                    jpegSizes = listOf(SizeInfo(8160, 6120)),
                    yuvSizes = listOf(SizeInfo(4080, 3060)),
                ),
            modes = ModeInfo(aeModes = listOf("OFF", "ON"), afModes = listOf("OFF", "AUTO")),
            android16 =
                Android16Info(
                    platformHasApi36 = true,
                    cctSupported = true,
                    cctKelvinMin = 2000,
                    cctKelvinMax = 10000,
                    colorTintKeyAvailable = true,
                    hybridAeSupported = true,
                    aePriorityModes = listOf("OFF", "SENSOR_EXPOSURE_TIME_PRIORITY", "SENSOR_SENSITIVITY_PRIORITY"),
                ),
            extensions = listOf("NIGHT"),
            requestKeys =
                mapOf(
                    "android.sensor.exposureTime" to true,
                    "android.sensor.sensitivity" to true,
                    "android.lens.focusDistance" to true,
                    "android.colorCorrection.colorTint" to true,
                ),
        )

    /** A bare-bones LEGACY-style camera: nothing manual, no RAW. */
    fun basicCamera(id: String = "1") =
        CameraInfo(
            id = id,
            kind = CameraKind.STANDALONE,
            facing = LensFacing.FRONT,
            hardwareLevel = "LIMITED",
            capabilities = listOf("BACKWARD_COMPATIBLE"),
            exposure = ExposureInfo(exposureTimeMinNs = 100_000L, exposureTimeMaxNs = SECOND_NS / 8),
            focus = FocusInfo(minimumFocusDistanceDiopters = 0f),
        )

    fun report(vararg cameras: CameraInfo) =
        CapabilityReport(
            generatedAt = "2026-10-04T23:10:00Z",
            app = AppInfo("Siderea", "0.1.0", 100),
            device =
                DeviceInfo(
                    manufacturer = "Acme",
                    brand = "acme",
                    model = "Test One",
                    device = "testone",
                    product = "testone",
                    hardware = "tst",
                    androidRelease = "16",
                    sdkInt = 36,
                    securityPatch = "2026-09-05",
                    fingerprint = "acme/testone/testone:16/TEST/1:user/release-keys",
                ),
            cameraIdsListed = cameras.map { it.id },
            concurrentCameraSets = listOf(listOf("0", "1")),
            cameras = cameras.toList(),
            readErrors = listOf("example error"),
        )
}
