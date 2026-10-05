package io.github.mrdarkdebug.siderea.core.camera.engine

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode
import io.github.mrdarkdebug.siderea.core.camera.control.PreviewExposure
import io.github.mrdarkdebug.siderea.core.camera.control.SensorCalibration
import io.github.mrdarkdebug.siderea.core.camera.control.WbMode
import io.github.mrdarkdebug.siderea.core.camera.control.WhiteBalance
import io.github.mrdarkdebug.siderea.core.camera.control.WhiteBalanceMath
import kotlin.math.roundToInt

/** How white balance is applied to a request. */
sealed interface WbPlan {
    /** Let the camera decide. */
    data object Auto : WbPlan

    /** One of the camera's own AWB modes, by name (`DAYLIGHT`, `INCANDESCENT`...). */
    data class Preset(
        val modeName: String,
    ) : WbPlan

    /** Android 16 colour-temperature control. */
    data class Cct(
        val kelvin: Int,
        val tint: Int,
    ) : WbPlan

    /** Computed gains and matrix, for cameras that offer neither of the above for the chosen value. */
    class Gains(
        val rggb: FloatArray,
        val transformRationals: IntArray,
        val calibrated: Boolean,
    ) : WbPlan
}

/** Android-free description of one capture request: everything the engine needs to set. */
class RequestPlan(
    val autoExposure: Boolean,
    val exposureCompensationSteps: Int,
    val shutterNs: Long,
    val iso: Int,
    val frameDurationNs: Long,
    val afMode: String,
    val focusDiopters: Float?,
    val wb: WbPlan,
    /** Multiplier the UI applies to the preview image (night view / long-exposure preview). */
    val displayGain: Float,
)

/**
 * Turns the user's [CaptureSettings] into a [RequestPlan], once for the live preview and once for the
 * photo. All clamping, the preview exposure cap and the white-balance decision happen here, in plain
 * Kotlin, so they are covered by unit tests rather than discovered on a phone.
 */
object RequestPlanner {
    /** 30 fps: the shortest frame duration used so fast shutters don't ask for an impossible frame rate. */
    private const val MIN_FRAME_DURATION_NS = 33_333_333L
    private const val STILL_FRAME_PADDING_NS = 1_000_000L

    private val presetKelvin =
        mapOf(
            WbMode.DAYLIGHT to 5200,
            WbMode.CLOUDY to 6500,
            WbMode.TUNGSTEN to 2850,
            WbMode.FLUORESCENT to 4000,
            WbMode.SHADE to 7500,
        )

    private val presetAwbName =
        mapOf(
            WbMode.DAYLIGHT to "DAYLIGHT",
            WbMode.CLOUDY to "CLOUDY_DAYLIGHT",
            WbMode.TUNGSTEN to "INCANDESCENT",
            WbMode.FLUORESCENT to "FLUORESCENT",
            WbMode.SHADE to "SHADE",
        )

    /**
     * @param effectiveShutterNs shutter in effect; differs from the setting in the software priority modes.
     * @param effectiveIso ISO in effect.
     * @param forPreview true for the viewfinder (long shutters are capped), false for the photo.
     */
    fun plan(
        settings: CaptureSettings,
        effectiveShutterNs: Long,
        effectiveIso: Int,
        limits: ExposureLimits,
        capabilities: EngineCapabilities,
        forPreview: Boolean,
    ): RequestPlan {
        val manualAe = settings.usesManualSensor && limits.manualExposure
        val shutter = limits.clampShutter(effectiveShutterNs)
        val iso = limits.clampIso(effectiveIso)
        val preview = if (manualAe && forPreview) PreviewExposure.forManual(shutter, iso, limits) else null
        val usedShutter = preview?.shutterNs ?: shutter
        val usedIso = preview?.iso ?: iso

        val frameDuration =
            when {
                !manualAe -> MIN_FRAME_DURATION_NS
                forPreview -> maxOf(usedShutter, MIN_FRAME_DURATION_NS)
                else -> maxOf(usedShutter + STILL_FRAME_PADDING_NS, MIN_FRAME_DURATION_NS)
            }.coerceAtMost(limits.maxFrameDurationNs)

        val manualFocus = settings.focusMode == FocusMode.MANUAL && limits.manualFocus
        return RequestPlan(
            autoExposure = !manualAe,
            exposureCompensationSteps = if (manualAe) 0 else evSteps(settings.evStops, limits),
            shutterNs = usedShutter,
            iso = usedIso,
            frameDurationNs = frameDuration,
            afMode = afMode(manualFocus, capabilities),
            focusDiopters = if (manualFocus) limits.clampFocus(settings.focusDiopters) else null,
            wb = whiteBalance(settings.whiteBalance, capabilities),
            displayGain = preview?.displayGain ?: 1f,
        )
    }

    fun evSteps(
        evStops: Float,
        limits: ExposureLimits,
    ): Int {
        if (limits.evStepStops <= 0f) return 0
        val steps = (limits.clampEv(evStops) / limits.evStepStops).roundToInt()
        return steps
    }

    private fun afMode(
        manualFocus: Boolean,
        capabilities: EngineCapabilities,
    ): String =
        when {
            manualFocus -> "OFF"
            "CONTINUOUS_PICTURE" in capabilities.afModes -> "CONTINUOUS_PICTURE"
            "AUTO" in capabilities.afModes -> "AUTO"
            else -> "OFF"
        }

    private fun whiteBalance(
        wb: WhiteBalance,
        capabilities: EngineCapabilities,
    ): WbPlan {
        if (wb.mode == WbMode.AUTO) return WbPlan.Auto
        if (wb.mode == WbMode.KELVIN) return kelvin(wb.kelvin, wb.tint, capabilities)
        val name = presetAwbName.getValue(wb.mode)
        if (name in capabilities.awbModes) return WbPlan.Preset(name)
        // The camera lacks that preset: compute the same light's white balance ourselves.
        return kelvin(presetKelvin.getValue(wb.mode), 0, capabilities)
    }

    private fun kelvin(
        kelvin: Int,
        tint: Int,
        capabilities: EngineCapabilities,
    ): WbPlan {
        if (capabilities.cct && "OFF" in capabilities.awbModes) return WbPlan.Cct(kelvin, tint)
        val solution = WhiteBalanceMath.solve(capabilities.calibration, kelvin, tint)
        return WbPlan.Gains(
            rggb = solution.rggbGains(),
            transformRationals = WhiteBalanceMath.toRationals(solution.transform),
            calibrated = solution.calibrated,
        )
    }
}

/** What the engine knows about the open camera that planning depends on. */
class EngineCapabilities(
    val afModes: Set<String>,
    val awbModes: Set<String>,
    /** Android 16 colour-temperature control is available (mode, range and request keys). */
    val cct: Boolean,
    val calibration: SensorCalibration?,
) {
    companion object {
        val NONE = EngineCapabilities(emptySet(), emptySet(), cct = false, calibration = null)
    }
}
