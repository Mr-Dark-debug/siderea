package io.github.mrdarkdebug.siderea.core.camera.control

import io.github.mrdarkdebug.siderea.core.camera.capability.CameraInfo
import io.github.mrdarkdebug.siderea.core.camera.capability.FeatureVerdicts
import io.github.mrdarkdebug.siderea.core.camera.capability.SizeInfo
import io.github.mrdarkdebug.siderea.core.camera.capability.SupportStatus

/**
 * What one lens can actually be asked to do, derived only from its reported characteristics.
 * Controls that a lens can't honour have a flag here, and the UI disables them with the reason.
 */
data class ExposureLimits(
    val manualExposure: Boolean,
    val shutterMinNs: Long,
    val shutterMaxNs: Long,
    val isoMin: Int,
    val isoMax: Int,
    val maxFrameDurationNs: Long,
    val evMinStops: Float,
    val evMaxStops: Float,
    val evStepStops: Float,
    val manualFocus: Boolean,
    /** Nearest focus distance in diopters; a conservative assumption when the camera doesn't say. */
    val focusNearDiopters: Float,
    val focusNearIsAssumed: Boolean,
    val raw: Boolean,
    val rawSizes: List<SizeInfo>,
    val jpegSizes: List<SizeInfo>,
    val manualFocusReason: String?,
    val manualExposureReason: String?,
    val rawReason: String?,
) {
    companion object {
        /** What we assume when a camera doesn't report its nearest focus distance (a typical 10 cm). */
        const val ASSUMED_NEAR_DIOPTERS = 10f

        private const val FALLBACK_SHUTTER_MIN_NS = 125_000L
        private const val FALLBACK_SHUTTER_MAX_NS = 100_000_000L
        private const val FALLBACK_ISO_MIN = 100
        private const val FALLBACK_ISO_MAX = 800
        private const val DEFAULT_EV_STEP = 1f / 3f

        fun from(
            camera: CameraInfo,
            sdkInt: Int,
        ): ExposureLimits {
            val verdicts = FeatureVerdicts.evaluate(camera, sdkInt).associateBy { it.id }
            val manualExposure = verdicts.getValue(FeatureVerdicts.MANUAL_EXPOSURE)
            val manualFocus = verdicts.getValue(FeatureVerdicts.MANUAL_FOCUS)
            val raw = verdicts.getValue(FeatureVerdicts.RAW_CAPTURE)
            val e = camera.exposure
            val near = camera.focus.minimumFocusDistanceDiopters
            val step = e.aeCompensationStep?.toFloat() ?: 0f
            val evStep = if (step > 0f) step else DEFAULT_EV_STEP
            return ExposureLimits(
                manualExposure = manualExposure.status == SupportStatus.SUPPORTED,
                shutterMinNs = e.exposureTimeMinNs ?: FALLBACK_SHUTTER_MIN_NS,
                shutterMaxNs = e.exposureTimeMaxNs ?: FALLBACK_SHUTTER_MAX_NS,
                isoMin = e.isoMin ?: FALLBACK_ISO_MIN,
                isoMax = e.isoMax ?: FALLBACK_ISO_MAX,
                maxFrameDurationNs = e.maxFrameDurationNs ?: e.exposureTimeMaxNs ?: FALLBACK_SHUTTER_MAX_NS,
                evMinStops = (e.aeCompensationMin ?: 0) * evStep,
                evMaxStops = (e.aeCompensationMax ?: 0) * evStep,
                evStepStops = evStep,
                manualFocus = manualFocus.status != SupportStatus.UNSUPPORTED,
                focusNearDiopters = if (near != null && near > 0f) near else ASSUMED_NEAR_DIOPTERS,
                focusNearIsAssumed = near == null || near <= 0f,
                raw = raw.status == SupportStatus.SUPPORTED,
                rawSizes = camera.outputs.rawSizes,
                jpegSizes = camera.outputs.jpegSizes,
                manualFocusReason = manualFocus.detail.takeIf { manualFocus.status != SupportStatus.SUPPORTED },
                manualExposureReason =
                    manualExposure.detail.takeIf {
                        manualExposure.status != SupportStatus.SUPPORTED
                    },
                rawReason = raw.detail.takeIf { raw.status != SupportStatus.SUPPORTED },
            )
        }
    }

    fun clampShutter(ns: Long): Long = ns.coerceIn(shutterMinNs, shutterMaxNs)

    fun clampIso(iso: Int): Int = iso.coerceIn(isoMin, isoMax)

    fun clampEv(stops: Float): Float = stops.coerceIn(evMinStops, evMaxStops)

    fun clampFocus(diopters: Float): Float = diopters.coerceIn(0f, focusNearDiopters)
}
