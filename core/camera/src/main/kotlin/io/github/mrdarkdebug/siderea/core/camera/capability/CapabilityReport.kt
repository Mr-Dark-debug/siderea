package io.github.mrdarkdebug.siderea.core.camera.capability

import kotlinx.serialization.Serializable

/**
 * A complete, serialisable snapshot of what this phone's cameras report.
 *
 * The capture engine is built on this data: no range, size or mode used anywhere in Siderea is
 * hardcoded. Raw values (nanoseconds, diopters) are stored as reported; display helpers live in
 * [CameraFormat].
 */
@Serializable
data class CapabilityReport(
    val schemaVersion: Int = SCHEMA_VERSION,
    /** ISO-8601, UTC. */
    val generatedAt: String,
    val app: AppInfo,
    val device: DeviceInfo,
    /** The ids Camera2 lists. Physical sub-cameras of a logical camera may not appear here. */
    val cameraIdsListed: List<String>,
    /** Sets of camera ids that can be open at the same time (API 30+). */
    val concurrentCameraSets: List<List<String>> = emptyList(),
    val cameras: List<CameraInfo>,
    /** Non-fatal problems met while reading, in plain language. */
    val readErrors: List<String> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class AppInfo(
    val name: String,
    val versionName: String,
    val versionCode: Long,
)

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val fingerprint: String,
)

@Serializable
enum class CameraKind {
    /** A camera with no physical sub-cameras. */
    STANDALONE,

    /** A logical multi-camera that fuses several physical cameras. */
    LOGICAL,

    /** A physical camera that sits behind a logical one. */
    PHYSICAL,
}

@Serializable
enum class LensFacing { BACK, FRONT, EXTERNAL, UNKNOWN }

@Serializable
data class CameraInfo(
    val id: String,
    val kind: CameraKind,
    val parentLogicalId: String? = null,
    val physicalIds: List<String> = emptyList(),
    val facing: LensFacing = LensFacing.UNKNOWN,
    val hardwareLevel: String = "UNKNOWN",
    val capabilities: List<String> = emptyList(),
    val manualSensor: Boolean = false,
    val raw: Boolean = false,
    val lens: LensInfo = LensInfo(),
    val sensor: SensorInfo = SensorInfo(),
    val exposure: ExposureInfo = ExposureInfo(),
    val focus: FocusInfo = FocusInfo(),
    val outputs: OutputInfo = OutputInfo(),
    val modes: ModeInfo = ModeInfo(),
    val android16: Android16Info = Android16Info(),
    /** Camera extensions (Night, HDR, ...) the vendor offers for this camera. */
    val extensions: List<String> = emptyList(),
    /** Whether selected capture-request keys are accepted by this camera. */
    val requestKeys: Map<String, Boolean> = emptyMap(),
    /** What Siderea can do on this lens, derived from everything above. */
    val features: List<FeatureSupport> = emptyList(),
    /** Set when this camera's characteristics could not be read. */
    val error: String? = null,
)

@Serializable
data class LensInfo(
    val focalLengthsMm: List<Float> = emptyList(),
    /** 35 mm-equivalent focal lengths, from sensor size. */
    val equivalentFocalLengthsMm: List<Float> = emptyList(),
    /** "0.5x", "1x", "5x" ... relative to the main lens facing the same way. */
    val zoomLabel: String? = null,
    val zoomRatioToMain: Float? = null,
    val apertures: List<Float> = emptyList(),
    val opticalStabilization: List<String> = emptyList(),
    /** Range of CONTROL_ZOOM_RATIO the camera accepts (API 30+). */
    val controlZoomRatioMin: Float? = null,
    val controlZoomRatioMax: Float? = null,
    val poseReference: String? = null,
)

@Serializable
data class SensorInfo(
    val physicalWidthMm: Float? = null,
    val physicalHeightMm: Float? = null,
    val pixelArrayWidth: Int? = null,
    val pixelArrayHeight: Int? = null,
    val activeArrayWidth: Int? = null,
    val activeArrayHeight: Int? = null,
    val megapixels: Float? = null,
    val pixelPitchMicrons: Float? = null,
    val colorFilterArrangement: String? = null,
    val whiteLevel: Int? = null,
    /** Black level for the 2x2 CFA pattern, in raster order. */
    val blackLevelPattern: List<Int> = emptyList(),
    val orientationDegrees: Int? = null,
    val timestampSource: String? = null,
    val lensShadingApplied: Boolean? = null,
    val ultraHighResolutionSensor: Boolean = false,
)

@Serializable
data class ExposureInfo(
    val exposureTimeMinNs: Long? = null,
    val exposureTimeMaxNs: Long? = null,
    val isoMin: Int? = null,
    val isoMax: Int? = null,
    val maxFrameDurationNs: Long? = null,
    val aeCompensationMin: Int? = null,
    val aeCompensationMax: Int? = null,
    val aeCompensationStep: Double? = null,
)

@Serializable
data class FocusInfo(
    /** In diopters. 0 means fixed focus. */
    val minimumFocusDistanceDiopters: Float? = null,
    val hyperfocalDiopters: Float? = null,
    val calibration: String? = null,
)

@Serializable
data class SizeInfo(
    val width: Int,
    val height: Int,
) {
    val megapixels: Float get() = width.toLong().times(height) / MEGA

    private companion object {
        const val MEGA = 1_000_000f
    }
}

@Serializable
data class OutputInfo(
    val rawSizes: List<SizeInfo> = emptyList(),
    val jpegSizes: List<SizeInfo> = emptyList(),
    val yuvSizes: List<SizeInfo> = emptyList(),
    /** Present only on cameras with a maximum-resolution sensor mode. */
    val maxResolutionRawSizes: List<SizeInfo> = emptyList(),
    val maxResolutionJpegSizes: List<SizeInfo> = emptyList(),
    val maxResolutionYuvSizes: List<SizeInfo> = emptyList(),
)

@Serializable
data class ModeInfo(
    val aeModes: List<String> = emptyList(),
    val awbModes: List<String> = emptyList(),
    val afModes: List<String> = emptyList(),
    val noiseReductionModes: List<String> = emptyList(),
    val edgeModes: List<String> = emptyList(),
    val hotPixelModes: List<String> = emptyList(),
    val colorCorrectionModes: List<String> = emptyList(),
)

/** Android 16 (API 36) manual-camera additions. Gated on platform version and on the camera HAL. */
@Serializable
data class Android16Info(
    /** True if this phone runs a platform that knows the API 36 keys at all. */
    val platformHasApi36: Boolean = false,
    /** COLOR_CORRECTION_MODE_CCT plus a colour-temperature range. */
    val cctSupported: Boolean = false,
    val cctKelvinMin: Int? = null,
    val cctKelvinMax: Int? = null,
    val colorTintKeyAvailable: Boolean = false,
    /** Hybrid auto-exposure: exposure-time or ISO priority modes. */
    val hybridAeSupported: Boolean = false,
    val aePriorityModes: List<String> = emptyList(),
)

@Serializable
enum class SupportStatus { SUPPORTED, LIMITED, UNSUPPORTED }

@Serializable
data class FeatureSupport(
    val id: String,
    val title: String,
    val status: SupportStatus,
    /** Plain-language explanation with a next step when the status is not SUPPORTED. */
    val detail: String,
)
