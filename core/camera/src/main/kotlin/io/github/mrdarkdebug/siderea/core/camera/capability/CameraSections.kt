package io.github.mrdarkdebug.siderea.core.camera.capability

data class InspectorRow(
    val label: String,
    val value: String,
)

data class InspectorSection(
    val title: String,
    val rows: List<InspectorRow>,
)

/**
 * Lays one [CameraInfo] out as labelled rows for the Capability Inspector. Pure, so the exact text a
 * user sees (and sends back to the developer) is covered by unit tests.
 */
object CameraSections {
    private const val NOT_REPORTED = "not reported"
    private const val SIZES_SHOWN = 4

    fun of(camera: CameraInfo): List<InspectorSection> =
        listOf(
            identity(camera),
            lens(camera),
            sensor(camera),
            exposure(camera),
            focus(camera),
            outputs(camera),
            modes(camera),
            android16(camera),
            extensions(camera),
        ).filter { it.rows.isNotEmpty() }

    /** `Camera 3 · BACK · 5x · PHYSICAL`, the one-line header of a camera card. */
    fun title(camera: CameraInfo): String =
        buildList {
            add("Camera ${camera.id}")
            add(camera.facing.name)
            camera.lens.zoomLabel?.let(::add)
            add(camera.kind.name)
        }.joinToString(" · ")

    fun summaryLine(camera: CameraInfo): String {
        if (camera.error != null) return "Could not be read"
        val shutter = camera.exposure.exposureTimeMaxNs?.let { "to ${CameraFormat.exposure(it)}" }
        return listOfNotNull(
            if (camera.manualSensor) "Manual" else "Auto only",
            if (camera.raw) "RAW" else "JPEG only",
            shutter,
        ).joinToString(" · ")
    }

    private fun identity(c: CameraInfo) =
        InspectorSection(
            "Identity",
            buildList {
                add(InspectorRow("Camera ID", c.id))
                add(InspectorRow("Kind", c.kind.name))
                c.parentLogicalId?.let { add(InspectorRow("Behind logical camera", it)) }
                if (c.physicalIds.isNotEmpty()) add(InspectorRow("Physical cameras", c.physicalIds.joinToString(", ")))
                add(InspectorRow("Facing", c.facing.name))
                add(InspectorRow("Hardware level", c.hardwareLevel))
                add(InspectorRow("Manual sensor", yesNo(c.manualSensor)))
                add(InspectorRow("RAW", yesNo(c.raw)))
                add(InspectorRow("Capabilities", c.capabilities.joinOrNone()))
                c.error?.let { add(InspectorRow("Error", it)) }
            },
        )

    private fun lens(c: CameraInfo): InspectorSection {
        val l = c.lens
        return InspectorSection(
            "Lens",
            buildList {
                if (l.focalLengthsMm.isNotEmpty()) {
                    add(InspectorRow("Focal length", l.focalLengthsMm.joinToString(", ") { CameraFormat.focal(it) }))
                }
                if (l.equivalentFocalLengthsMm.isNotEmpty()) {
                    add(
                        InspectorRow(
                            "35 mm equivalent",
                            l.equivalentFocalLengthsMm.joinToString(", ") { CameraFormat.focal(it) },
                        ),
                    )
                }
                l.zoomLabel?.let { add(InspectorRow("Zoom vs main lens", it)) }
                if (l.apertures.isNotEmpty()) {
                    add(InspectorRow("Aperture", l.apertures.joinToString(", ") { CameraFormat.aperture(it) }))
                }
                if (l.opticalStabilization.isNotEmpty()) {
                    add(InspectorRow("Stabilisation", l.opticalStabilization.joinToString(", ")))
                }
                val zoomMin = l.controlZoomRatioMin
                val zoomMax = l.controlZoomRatioMax
                if (zoomMin != null && zoomMax != null) {
                    val range = "${CameraFormat.number(zoomMin, 1)}–${CameraFormat.number(zoomMax, 1)}x"
                    add(InspectorRow("Zoom ratio range", range))
                }
                l.poseReference?.let { add(InspectorRow("Pose reference", it)) }
            },
        )
    }

    private fun sensor(c: CameraInfo): InspectorSection {
        val s = c.sensor
        return InspectorSection(
            "Sensor",
            buildList {
                val width = s.physicalWidthMm
                val height = s.physicalHeightMm
                if (width != null && height != null) {
                    val size = "${CameraFormat.number(width, 2)} × ${CameraFormat.number(height, 2)} mm"
                    add(InspectorRow("Physical size", size))
                }
                if (s.pixelArrayWidth != null && s.pixelArrayHeight != null) {
                    val mp = s.megapixels?.let { " (${"%.1f".format(java.util.Locale.ROOT, it)} MP)" }.orEmpty()
                    add(InspectorRow("Pixel array", "${s.pixelArrayWidth} × ${s.pixelArrayHeight}$mp"))
                }
                s.pixelPitchMicrons?.let {
                    add(InspectorRow("Pixel pitch", "${"%.2f".format(java.util.Locale.ROOT, it)} µm"))
                }
                s.colorFilterArrangement?.let { add(InspectorRow("Colour filter", it)) }
                s.whiteLevel?.let { add(InspectorRow("White level", it.toString())) }
                if (s.blackLevelPattern.isNotEmpty()) {
                    add(InspectorRow("Black level", s.blackLevelPattern.joinToString(", ")))
                }
                s.orientationDegrees?.let { add(InspectorRow("Orientation", "$it°")) }
                s.timestampSource?.let { add(InspectorRow("Timestamp source", it)) }
                s.lensShadingApplied?.let { add(InspectorRow("Lens shading applied", yesNo(it))) }
                add(InspectorRow("Full-resolution mode", yesNo(s.ultraHighResolutionSensor)))
            },
        )
    }

    private fun exposure(c: CameraInfo): InspectorSection {
        val e = c.exposure
        return InspectorSection(
            "Exposure",
            buildList {
                if (e.exposureTimeMinNs != null && e.exposureTimeMaxNs != null) {
                    add(
                        InspectorRow(
                            "Shutter range",
                            "${CameraFormat.exposure(
                                e.exposureTimeMinNs,
                            )} – ${CameraFormat.exposure(e.exposureTimeMaxNs)}",
                        ),
                    )
                } else {
                    add(InspectorRow("Shutter range", NOT_REPORTED))
                }
                if (e.isoMin != null && e.isoMax != null) {
                    add(InspectorRow("ISO range", "${e.isoMin} – ${e.isoMax}"))
                } else {
                    add(InspectorRow("ISO range", NOT_REPORTED))
                }
                e.maxFrameDurationNs?.let {
                    add(
                        InspectorRow(
                            "Max frame duration",
                            "${CameraFormat.exposure(it)} (${CameraFormat.frameRate(it)})",
                        ),
                    )
                }
                if (e.aeCompensationMin != null && e.aeCompensationMax != null && e.aeCompensationStep != null) {
                    add(
                        InspectorRow(
                            "Exposure compensation",
                            "${CameraFormat.ev(e.aeCompensationMin, e.aeCompensationStep)} to " +
                                CameraFormat.ev(e.aeCompensationMax, e.aeCompensationStep),
                        ),
                    )
                }
            },
        )
    }

    private fun focus(c: CameraInfo): InspectorSection {
        val f = c.focus
        return InspectorSection(
            "Focus",
            buildList {
                f.minimumFocusDistanceDiopters?.let { dpt ->
                    val text =
                        if (dpt <= 0f) {
                            "fixed focus"
                        } else {
                            "${CameraFormat.distance(CameraFormat.diopterToMeters(dpt))} ($dpt dpt)"
                        }
                    add(InspectorRow("Nearest focus", text))
                }
                f.hyperfocalDiopters?.let { dpt ->
                    add(
                        InspectorRow(
                            "Hyperfocal distance",
                            "${CameraFormat.distance(CameraFormat.diopterToMeters(dpt))} ($dpt dpt)",
                        ),
                    )
                }
                f.calibration?.let { add(InspectorRow("Distance calibration", it)) }
            },
        )
    }

    private fun outputs(c: CameraInfo): InspectorSection {
        val o = c.outputs
        return InspectorSection(
            "Output sizes",
            buildList {
                add(InspectorRow("RAW", sizesText(o.rawSizes)))
                add(InspectorRow("JPEG", sizesText(o.jpegSizes)))
                add(InspectorRow("YUV", sizesText(o.yuvSizes)))
                if (o.maxResolutionRawSizes.isNotEmpty()) {
                    add(InspectorRow("RAW (max-res mode)", sizesText(o.maxResolutionRawSizes)))
                }
                if (o.maxResolutionJpegSizes.isNotEmpty()) {
                    add(InspectorRow("JPEG (max-res mode)", sizesText(o.maxResolutionJpegSizes)))
                }
            },
        )
    }

    private fun modes(c: CameraInfo): InspectorSection {
        val m = c.modes
        return InspectorSection(
            "Modes",
            listOf(
                InspectorRow("Auto-exposure", m.aeModes.joinOrNone()),
                InspectorRow("White balance", m.awbModes.joinOrNone()),
                InspectorRow("Autofocus", m.afModes.joinOrNone()),
                InspectorRow("Noise reduction", m.noiseReductionModes.joinOrNone()),
                InspectorRow("Edge enhancement", m.edgeModes.joinOrNone()),
                InspectorRow("Hot-pixel correction", m.hotPixelModes.joinOrNone()),
                InspectorRow("Colour correction", m.colorCorrectionModes.joinOrNone()),
            ),
        )
    }

    private fun android16(c: CameraInfo): InspectorSection {
        val a = c.android16
        return InspectorSection(
            "Android 16 additions",
            buildList {
                add(InspectorRow("Platform has API 36", yesNo(a.platformHasApi36)))
                add(InspectorRow("Kelvin (CCT) white balance", yesNo(a.cctSupported)))
                if (a.cctKelvinMin != null && a.cctKelvinMax != null) {
                    add(InspectorRow("Kelvin range", "${a.cctKelvinMin} – ${a.cctKelvinMax} K"))
                }
                add(InspectorRow("Tint control", yesNo(a.colorTintKeyAvailable)))
                add(InspectorRow("Hybrid auto-exposure", yesNo(a.hybridAeSupported)))
                if (a.aePriorityModes.isNotEmpty()) {
                    add(
                        InspectorRow("Priority modes", a.aePriorityModes.joinToString(", ")),
                    )
                }
            },
        )
    }

    private fun extensions(c: CameraInfo) =
        InspectorSection(
            "Extensions and request keys",
            buildList {
                add(InspectorRow("Camera extensions", c.extensions.joinOrNone()))
                c.requestKeys.forEach { (key, ok) ->
                    add(InspectorRow(key.removePrefix("android."), if (ok) "accepted" else "not accepted"))
                }
            },
        )

    private fun sizesText(sizes: List<SizeInfo>): String {
        if (sizes.isEmpty()) return "none"
        val shown = sizes.take(SIZES_SHOWN).joinToString(", ") { CameraFormat.size(it) }
        val more = sizes.size - SIZES_SHOWN
        return if (more > 0) "$shown, +$more more" else shown
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun List<String>.joinOrNone() = if (isEmpty()) "none" else joinToString(", ")
}
