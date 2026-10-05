package io.github.mrdarkdebug.siderea.ui.camera

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.control.FocusMode

/**
 * Pure rules for editing [CaptureSettings]. Kept out of the ViewModel so every combination is unit-tested.
 */
object CameraSettingsOps {
    /**
     * Shutter and ISO are each either automatic or manual; the four combinations are the four exposure
     * modes of a camera body: both auto (P), shutter fixed (S), ISO fixed (I), both fixed (M).
     */
    fun modeOf(
        shutterManual: Boolean,
        isoManual: Boolean,
    ): ExposureMode =
        when {
            shutterManual && isoManual -> ExposureMode.MANUAL
            shutterManual -> ExposureMode.SHUTTER_PRIORITY
            isoManual -> ExposureMode.ISO_PRIORITY
            else -> ExposureMode.AUTO
        }

    fun withShutterManual(
        settings: CaptureSettings,
        manual: Boolean,
    ): CaptureSettings = settings.copy(exposureMode = modeOf(manual, settings.isIsoManual))

    fun withIsoManual(
        settings: CaptureSettings,
        manual: Boolean,
    ): CaptureSettings = settings.copy(exposureMode = modeOf(settings.isShutterManual, manual))

    /** True when exposure compensation still means something (anything except full manual). */
    fun evApplies(settings: CaptureSettings): Boolean = settings.exposureMode != ExposureMode.MANUAL

    /**
     * Brings [settings] inside what a lens can do: values clamped to its ranges, and any control the lens
     * can't honour put back to automatic or to a format it can produce.
     */
    fun coerce(
        settings: CaptureSettings,
        limits: ExposureLimits,
    ): CaptureSettings {
        var s =
            settings.copy(
                shutterNs = limits.clampShutter(settings.shutterNs),
                iso = limits.clampIso(settings.iso),
                evStops = limits.clampEv(settings.evStops),
                focusDiopters = limits.clampFocus(settings.focusDiopters),
            )
        if (!limits.manualExposure) s = s.copy(exposureMode = ExposureMode.AUTO)
        if (!limits.manualFocus) s = s.copy(focusMode = FocusMode.AUTO)
        if (!limits.raw && s.format.wantsRaw) s = s.copy(format = CaptureFormat.JPEG)
        return s
    }

    /** JPEG -> RAW -> RAW+JPEG -> JPEG, skipping RAW formats a lens can't produce. */
    fun nextFormat(
        current: CaptureFormat,
        rawAvailable: Boolean,
    ): CaptureFormat {
        if (!rawAvailable) return CaptureFormat.JPEG
        return when (current) {
            CaptureFormat.JPEG -> CaptureFormat.RAW_JPEG
            CaptureFormat.RAW_JPEG -> CaptureFormat.RAW
            CaptureFormat.RAW -> CaptureFormat.JPEG
        }
    }

    fun formatLabel(format: CaptureFormat): String =
        when (format) {
            CaptureFormat.JPEG -> "JPG"
            CaptureFormat.RAW -> "RAW"
            CaptureFormat.RAW_JPEG -> "RAW+JPG"
        }

    /** Self-timer choices in seconds; the button cycles through them in order. */
    val TIMER_CHOICES = listOf(0, 2, 5, 10)

    fun nextTimer(current: Int): Int = TIMER_CHOICES[(TIMER_CHOICES.indexOf(current) + 1) % TIMER_CHOICES.size]
}
