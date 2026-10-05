package io.github.mrdarkdebug.siderea.core.camera.capability

/**
 * Turns raw capabilities into "what can Siderea do on this lens", in plain language.
 *
 * The UI shows these verbatim, and later milestones use the same rules to enable or disable
 * controls per lens, so there is a single source of truth for what a lens can do.
 */
object FeatureVerdicts {
    const val MANUAL_EXPOSURE = "manual_exposure"
    const val LONG_EXPOSURE = "long_exposure"
    const val RAW_CAPTURE = "raw_capture"
    const val MANUAL_FOCUS = "manual_focus"
    const val KELVIN_WB = "kelvin_white_balance"
    const val HYBRID_AE = "hybrid_ae"
    const val FULL_RESOLUTION = "full_resolution"
    const val NIGHT_EXTENSION = "night_extension"

    /** Single exposures at least this long are good enough for sky work without stacking. */
    const val ASTRO_GRADE_EXPOSURE_NS = 10_000_000_000L

    private const val KEY_EXPOSURE = "android.sensor.exposureTime"
    private const val KEY_SENSITIVITY = "android.sensor.sensitivity"
    private const val KEY_FOCUS = "android.lens.focusDistance"
    private const val KEY_GAINS = "android.colorCorrection.gains"
    private const val ANDROID_16 = 36

    fun evaluate(
        camera: CameraInfo,
        sdkInt: Int,
    ): List<FeatureSupport> =
        listOf(
            manualExposure(camera),
            longExposure(camera),
            rawCapture(camera),
            manualFocus(camera),
            kelvinWhiteBalance(camera, sdkInt),
            hybridAe(camera, sdkInt),
            fullResolution(camera),
            nightExtension(camera),
        )

    private fun manualExposure(c: CameraInfo): FeatureSupport {
        val keysOk = c.requestKeys[KEY_EXPOSURE] != false && c.requestKeys[KEY_SENSITIVITY] != false
        val hasRanges = c.exposure.exposureTimeMaxNs != null && c.exposure.isoMax != null
        return when {
            c.manualSensor && hasRanges && keysOk -> {
                supported(
                    MANUAL_EXPOSURE,
                    "Manual shutter and ISO",
                    "Shutter ${c.exposure.shutterRangeText()}, ISO ${c.exposure.isoMin}–${c.exposure.isoMax}.",
                )
            }

            else -> {
                unsupported(
                    MANUAL_EXPOSURE,
                    "Manual shutter and ISO",
                    "This lens doesn't report MANUAL_SENSOR, so shutter and ISO stay automatic. " +
                        "Use exposure compensation, or switch to another lens.",
                )
            }
        }
    }

    private fun longExposure(c: CameraInfo): FeatureSupport {
        val max = c.exposure.exposureTimeMaxNs
        val title = "Longest single exposure"
        return when {
            !c.manualSensor || max == null -> {
                unsupported(
                    LONG_EXPOSURE,
                    title,
                    "Without manual sensor control this lens can't be set to a long exposure. Pick another lens.",
                )
            }

            max >= ASTRO_GRADE_EXPOSURE_NS -> {
                supported(LONG_EXPOSURE, title, CameraFormat.exposure(max))
            }

            else -> {
                FeatureSupport(
                    id = LONG_EXPOSURE,
                    title = title,
                    status = SupportStatus.LIMITED,
                    detail =
                        "This lens can't expose longer than ${CameraFormat.exposure(max)}. " +
                            "Use Virtual Bulb for longer exposures.",
                )
            }
        }
    }

    private fun rawCapture(c: CameraInfo): FeatureSupport {
        val title = "RAW (DNG) capture"
        return when {
            c.raw && c.outputs.rawSizes.isNotEmpty() -> {
                val largest = c.outputs.rawSizes.first()
                supported(RAW_CAPTURE, title, "Up to ${CameraFormat.size(largest)}.")
            }

            c.raw -> {
                FeatureSupport(
                    RAW_CAPTURE,
                    title,
                    SupportStatus.LIMITED,
                    "The camera claims RAW but lists no RAW output size. Siderea will shoot JPEG on this lens.",
                )
            }

            else -> {
                unsupported(RAW_CAPTURE, title, "This lens has no RAW output. Siderea will shoot JPEG on it.")
            }
        }
    }

    /**
     * Manual focus needs a focus-distance key the HAL accepts and a lens that actually moves.
     *
     * A lens that moves is detected from its autofocus modes as well as from the nearest-focus value,
     * because real phones (the Pixel 10 among them) report a null nearest-focus distance while having a
     * working autofocus motor. An explicit 0 means the camera says it is fixed-focus.
     */
    private fun manualFocus(c: CameraInfo): FeatureSupport {
        val title = "Manual focus"
        val minFocus = c.focus.minimumFocusDistanceDiopters
        val keyOk = c.requestKeys[KEY_FOCUS] != false
        val lensMoves = c.modes.afModes.any { it != "OFF" } || (minFocus != null && minFocus > 0f)
        val reportedFixed = minFocus != null && minFocus <= 0f
        return when {
            !keyOk -> {
                unsupported(
                    MANUAL_FOCUS,
                    title,
                    "This camera doesn't accept a focus distance. Siderea will use autofocus here.",
                )
            }

            reportedFixed || !lensMoves -> {
                unsupported(
                    MANUAL_FOCUS,
                    title,
                    "This lens is fixed-focus. Focus assist is unavailable here; choose another lens for stars.",
                )
            }

            minFocus == null -> {
                FeatureSupport(
                    MANUAL_FOCUS,
                    title,
                    SupportStatus.LIMITED,
                    "Manual focus works, but this camera doesn't report its nearest focus distance. Siderea " +
                        "assumes 10 cm and learns the real limit while autofocusing. Infinity is exact enough " +
                        "for stars: confirm it with the zoomed preview and focus peaking.",
                )
            }

            c.focus.calibration == "UNCALIBRATED" -> {
                FeatureSupport(
                    MANUAL_FOCUS,
                    title,
                    SupportStatus.LIMITED,
                    "Focus distance is not calibrated on this lens, so the ∞ position may not be exactly " +
                        "infinity. Confirm focus on a star with the zoomed preview.",
                )
            }

            else -> {
                supported(
                    MANUAL_FOCUS,
                    title,
                    "Nearest ${CameraFormat.distance(CameraFormat.diopterToMeters(minFocus))}, ∞ available.",
                )
            }
        }
    }

    private fun kelvinWhiteBalance(
        c: CameraInfo,
        sdkInt: Int,
    ): FeatureSupport {
        val title = "Kelvin and tint white balance"
        val a16 = c.android16
        return when {
            a16.cctSupported && a16.colorTintKeyAvailable -> {
                supported(
                    KELVIN_WB,
                    title,
                    "${a16.cctKelvinMin}–${a16.cctKelvinMax} K with tint.",
                )
            }

            a16.cctSupported -> {
                FeatureSupport(
                    KELVIN_WB,
                    title,
                    SupportStatus.LIMITED,
                    "Kelvin is supported (${a16.cctKelvinMin}–${a16.cctKelvinMax} K) " +
                        "but this camera has no tint control.",
                )
            }

            sdkInt < ANDROID_16 -> {
                unsupported(
                    KELVIN_WB,
                    title,
                    "Precise Kelvin and tint need Android 16. On this version Siderea offers the standard " +
                        "white-balance presets.",
                )
            }

            c.requestKeys[KEY_GAINS] == true -> {
                FeatureSupport(
                    KELVIN_WB,
                    title,
                    SupportStatus.LIMITED,
                    "This camera doesn't offer Android 16 Kelvin/tint. Siderea computes white balance itself from " +
                        "the sensor's colour calibration (gains and colour matrix): accurate in ordinary light, " +
                        "not lab-grade.",
                )
            }

            else -> {
                unsupported(
                    KELVIN_WB,
                    title,
                    "This camera exposes neither CCT nor manual gains. Use the white-balance presets.",
                )
            }
        }
    }

    private fun hybridAe(
        c: CameraInfo,
        sdkInt: Int,
    ): FeatureSupport {
        val title = "Hybrid auto-exposure (ISO or shutter priority)"
        return when {
            c.android16.hybridAeSupported -> {
                supported(
                    HYBRID_AE,
                    title,
                    c.android16.aePriorityModes.joinToString(", "),
                )
            }

            sdkInt < ANDROID_16 -> {
                software(
                    title,
                    "Android 16 adds hardware priority modes; on this phone Siderea provides its own " +
                        "software auto-ISO and auto-shutter.",
                )
            }

            else -> {
                software(
                    title,
                    "This camera has no exposure-priority modes, so Siderea provides its own " +
                        "software auto-ISO and auto-shutter.",
                )
            }
        }
    }

    private fun software(
        title: String,
        detail: String,
    ) = FeatureSupport(HYBRID_AE, title, SupportStatus.LIMITED, detail)

    private fun fullResolution(c: CameraInfo): FeatureSupport {
        val title = "Full-resolution sensor mode"
        val largest = c.outputs.maxResolutionRawSizes.firstOrNull() ?: c.outputs.maxResolutionJpegSizes.firstOrNull()
        return if (c.sensor.ultraHighResolutionSensor && largest != null) {
            supported(FULL_RESOLUTION, title, "Up to ${CameraFormat.size(largest)}.")
        } else {
            unsupported(FULL_RESOLUTION, title, "This lens has a single sensor mode; Siderea uses the default one.")
        }
    }

    private fun nightExtension(c: CameraInfo): FeatureSupport {
        val title = "Vendor Night extension"
        return if ("NIGHT" in c.extensions) {
            supported(NIGHT_EXTENSION, title, "Available, but Siderea does its own long exposure and stacking.")
        } else {
            unsupported(NIGHT_EXTENSION, title, "Not offered. Siderea doesn't need it.")
        }
    }

    private fun ExposureInfo.shutterRangeText(): String {
        val min = exposureTimeMinNs?.let { CameraFormat.exposure(it) } ?: "?"
        val max = exposureTimeMaxNs?.let { CameraFormat.exposure(it) } ?: "?"
        return "$min – $max"
    }

    private fun supported(
        id: String,
        title: String,
        detail: String,
    ) = FeatureSupport(id, title, SupportStatus.SUPPORTED, detail)

    private fun unsupported(
        id: String,
        title: String,
        detail: String,
    ) = FeatureSupport(id, title, SupportStatus.UNSUPPORTED, detail)
}
