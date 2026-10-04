package io.github.mrdarkdebug.siderea.core.camera.capability

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.annotation.RequiresApi

/**
 * Reads one [CameraCharacteristics] into a [CameraInfo]. Everything here only reports what the camera
 * says; nothing is assumed. Keys newer than minSdk are read behind `SDK_INT` checks and a missing key
 * simply stays null.
 */
internal object CharacteristicsMapper {
    private val requestKeysOfInterest =
        listOf(
            "android.sensor.exposureTime",
            "android.sensor.sensitivity",
            "android.sensor.frameDuration",
            "android.lens.focusDistance",
            "android.colorCorrection.colorTemperature",
            "android.colorCorrection.colorTint",
            "android.colorCorrection.gains",
            "android.colorCorrection.transform",
            "android.control.aePriorityMode",
            "android.control.zoomRatio",
            "android.control.aeLock",
            "android.control.awbLock",
            "android.noiseReduction.mode",
            "android.hotPixel.mode",
            "android.statistics.lensShadingMapMode",
        )

    @Suppress("LongMethod")
    fun map(
        id: String,
        chars: CameraCharacteristics,
        kind: CameraKind,
        parentLogicalId: String?,
        physicalIds: List<String>,
        extensions: List<String>,
    ): CameraInfo {
        val capabilityNames =
            chars
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                .orEmpty()
                .map(EnumNames::capability)
                .sorted()
        val availableKeys = chars.availableCaptureRequestKeys.mapTo(HashSet()) { it.name }
        val sensor = sensor(chars, "ULTRA_HIGH_RESOLUTION_SENSOR" in capabilityNames)
        val lens = lens(chars, sensor)
        val exposure = exposure(chars)
        val modes = modes(chars)
        val outputs = outputs(chars)
        val android16 = android16(chars, availableKeys)
        val info =
            CameraInfo(
                id = id,
                kind = kind,
                parentLogicalId = parentLogicalId,
                physicalIds = physicalIds,
                facing = EnumNames.lensFacing(chars.get(CameraCharacteristics.LENS_FACING)),
                hardwareLevel = EnumNames.hardwareLevel(chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)),
                capabilities = capabilityNames,
                manualSensor = "MANUAL_SENSOR" in capabilityNames,
                raw = "RAW" in capabilityNames,
                lens = lens,
                sensor = sensor,
                exposure = exposure,
                focus = focus(chars),
                outputs = outputs,
                modes = modes,
                android16 = android16,
                extensions = extensions,
                requestKeys = requestKeysOfInterest.associateWith { it in availableKeys },
            )
        return info.copy(features = FeatureVerdicts.evaluate(info, Build.VERSION.SDK_INT))
    }

    private fun sensor(
        chars: CameraCharacteristics,
        ultraHighRes: Boolean,
    ): SensorInfo {
        val physical = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixelArray = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val black = chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
        return SensorInfo(
            physicalWidthMm = physical?.width,
            physicalHeightMm = physical?.height,
            pixelArrayWidth = pixelArray?.width,
            pixelArrayHeight = pixelArray?.height,
            activeArrayWidth = active?.width(),
            activeArrayHeight = active?.height(),
            megapixels = pixelArray?.let { CameraFormat.megapixels(it.width, it.height) },
            pixelPitchMicrons =
                if (physical != null && pixelArray != null && pixelArray.width > 0) {
                    physical.width / pixelArray.width * MICRONS_PER_MM
                } else {
                    null
                },
            colorFilterArrangement =
                chars
                    .get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
                    ?.let(EnumNames::colorFilterArrangement),
            whiteLevel = chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL),
            blackLevelPattern =
                black
                    ?.let {
                        listOf(
                            it.getOffsetForIndex(0, 0),
                            it.getOffsetForIndex(1, 0),
                            it.getOffsetForIndex(0, 1),
                            it.getOffsetForIndex(1, 1),
                        )
                    }.orEmpty(),
            orientationDegrees = chars.get(CameraCharacteristics.SENSOR_ORIENTATION),
            timestampSource =
                chars
                    .get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
                    ?.let(EnumNames::timestampSource),
            lensShadingApplied = chars.get(CameraCharacteristics.SENSOR_INFO_LENS_SHADING_APPLIED),
            ultraHighResolutionSensor = ultraHighRes,
        )
    }

    private fun lens(
        chars: CameraCharacteristics,
        sensor: SensorInfo,
    ): LensInfo {
        val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
        val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) zoomRatioRange(chars) else null
        return LensInfo(
            focalLengthsMm = focals,
            equivalentFocalLengthsMm =
                focals.mapNotNull {
                    ZoomLabels.equivalentFocalLengthMm(it, sensor.physicalWidthMm, sensor.physicalHeightMm)
                },
            apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty(),
            opticalStabilization =
                chars
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    .orEmpty()
                    .map(EnumNames::opticalStabilization),
            controlZoomRatioMin = zoomRange?.lower,
            controlZoomRatioMax = zoomRange?.upper,
            poseReference = chars.get(CameraCharacteristics.LENS_POSE_REFERENCE)?.let(EnumNames::poseReference),
        )
    }

    private fun exposure(chars: CameraCharacteristics): ExposureInfo {
        val time = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val iso = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val compensation = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        return ExposureInfo(
            exposureTimeMinNs = time?.lower,
            exposureTimeMaxNs = time?.upper,
            isoMin = iso?.lower,
            isoMax = iso?.upper,
            maxFrameDurationNs = chars.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION),
            aeCompensationMin = compensation?.lower,
            aeCompensationMax = compensation?.upper,
            aeCompensationStep = step?.toDouble(),
        )
    }

    private fun focus(chars: CameraCharacteristics) =
        FocusInfo(
            minimumFocusDistanceDiopters = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE),
            hyperfocalDiopters = chars.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE),
            calibration =
                chars
                    .get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
                    ?.let(EnumNames::focusCalibration),
        )

    private fun modes(chars: CameraCharacteristics) =
        ModeInfo(
            aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES).orEmpty().map(EnumNames::aeMode),
            awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES).orEmpty().map(EnumNames::awbMode),
            afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES).orEmpty().map(EnumNames::afMode),
            noiseReductionModes =
                chars
                    .get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                    .orEmpty()
                    .map(EnumNames::noiseReductionMode),
            edgeModes = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES).orEmpty().map(EnumNames::edgeMode),
            hotPixelModes =
                chars
                    .get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
                    .orEmpty()
                    .map(EnumNames::hotPixelMode),
            colorCorrectionModes =
                if (Build.VERSION.SDK_INT >= ANDROID_16) colorCorrectionModes(chars) else emptyList(),
        )

    private fun outputs(chars: CameraCharacteristics): OutputInfo {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val maxRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) maxResolutionMap(chars) else null
        return OutputInfo(
            rawSizes = sizes(map, ImageFormat.RAW_SENSOR),
            jpegSizes = sizes(map, ImageFormat.JPEG),
            yuvSizes = sizes(map, ImageFormat.YUV_420_888),
            maxResolutionRawSizes = sizes(maxRes, ImageFormat.RAW_SENSOR),
            maxResolutionJpegSizes = sizes(maxRes, ImageFormat.JPEG),
            maxResolutionYuvSizes = sizes(maxRes, ImageFormat.YUV_420_888),
        )
    }

    private fun android16(
        chars: CameraCharacteristics,
        availableKeys: Set<String>,
    ): Android16Info {
        if (Build.VERSION.SDK_INT < ANDROID_16) return Android16Info(platformHasApi36 = false)
        val ccTempRange = cctRange(chars)
        val priorityModes = aePriorityModes(chars)
        val modes = colorCorrectionModes(chars)
        return Android16Info(
            platformHasApi36 = true,
            cctSupported = "CCT" in modes && ccTempRange != null,
            cctKelvinMin = ccTempRange?.lower,
            cctKelvinMax = ccTempRange?.upper,
            colorTintKeyAvailable = "android.colorCorrection.colorTint" in availableKeys,
            hybridAeSupported = priorityModes.any { it != "OFF" },
            aePriorityModes = priorityModes,
        )
    }

    private fun sizes(
        map: StreamConfigurationMap?,
        format: Int,
    ): List<SizeInfo> {
        if (map == null) return emptyList()
        val all = LinkedHashSet<Size>()
        map.getOutputSizes(format)?.let(all::addAll)
        // Sizes that can't sustain ~30 fps (typically full-resolution) are listed separately by Camera2.
        // A still camera wants them too.
        map.getHighResolutionOutputSizes(format)?.let(all::addAll)
        return all.sortedByDescending { it.width.toLong() * it.height }.map { SizeInfo(it.width, it.height) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun zoomRatioRange(chars: CameraCharacteristics): Range<Float>? =
        chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun maxResolutionMap(chars: CameraCharacteristics): StreamConfigurationMap? =
        chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)

    /** The list of colour-correction modes was only exposed to apps in Android 16. */
    @RequiresApi(ANDROID_16)
    private fun colorCorrectionModes(chars: CameraCharacteristics): List<String> =
        chars
            .get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES)
            .orEmpty()
            .map(EnumNames::colorCorrectionMode)

    @RequiresApi(ANDROID_16)
    private fun cctRange(chars: CameraCharacteristics): Range<Int>? =
        chars.get(CameraCharacteristics.COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE)

    @RequiresApi(ANDROID_16)
    private fun aePriorityModes(chars: CameraCharacteristics): List<String> =
        chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES).orEmpty().map(EnumNames::aePriorityMode)

    private const val ANDROID_16 = 36
    private const val MICRONS_PER_MM = 1000f
}

private fun IntArray?.orEmpty(): IntArray = this ?: IntArray(0)
