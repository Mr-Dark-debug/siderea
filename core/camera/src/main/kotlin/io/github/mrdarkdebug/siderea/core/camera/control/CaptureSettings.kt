package io.github.mrdarkdebug.siderea.core.camera.control

import kotlinx.serialization.Serializable

/**
 * How exposure is decided.
 *
 * `SHUTTER_PRIORITY` and `ISO_PRIORITY` ("S" and "I" on a camera body) are implemented in software by
 * [SoftwareAe], because many phones, including the Pixel 10, expose no hardware priority modes.
 */
@Serializable
enum class ExposureMode { AUTO, MANUAL, SHUTTER_PRIORITY, ISO_PRIORITY }

@Serializable
enum class FocusMode { AUTO, MANUAL }

@Serializable
enum class CaptureFormat(
    val wantsRaw: Boolean,
    val wantsJpeg: Boolean,
) {
    JPEG(wantsRaw = false, wantsJpeg = true),
    RAW(wantsRaw = true, wantsJpeg = false),
    RAW_JPEG(wantsRaw = true, wantsJpeg = true),
}

@Serializable
enum class WbMode { AUTO, DAYLIGHT, CLOUDY, TUNGSTEN, FLUORESCENT, SHADE, KELVIN }

@Serializable
data class WhiteBalance(
    val mode: WbMode = WbMode.AUTO,
    val kelvin: Int = 5500,
    /** Green(-) to magenta(+), roughly Lightroom's -150..+150. */
    val tint: Int = 0,
)

/** Everything the user can set for a photo. The engine turns it into capture requests. */
@Serializable
data class CaptureSettings(
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val shutterNs: Long = DEFAULT_SHUTTER_NS,
    val iso: Int = DEFAULT_ISO,
    /** Exposure compensation, in stops. Applies in automatic modes. */
    val evStops: Float = 0f,
    val focusMode: FocusMode = FocusMode.AUTO,
    /** 0 is infinity; larger is nearer (diopters, 1 / metres). */
    val focusDiopters: Float = 0f,
    val whiteBalance: WhiteBalance = WhiteBalance(),
    val format: CaptureFormat = CaptureFormat.JPEG,
) {
    val isShutterManual: Boolean
        get() = exposureMode == ExposureMode.MANUAL || exposureMode == ExposureMode.SHUTTER_PRIORITY

    val isIsoManual: Boolean
        get() = exposureMode == ExposureMode.MANUAL || exposureMode == ExposureMode.ISO_PRIORITY

    /** True when the camera must run with auto-exposure off (our own values are applied). */
    val usesManualSensor: Boolean
        get() = exposureMode != ExposureMode.AUTO

    companion object {
        const val DEFAULT_SHUTTER_NS = 8_333_333L
        const val DEFAULT_ISO = 100
    }
}
