package io.github.mrdarkdebug.siderea.core.camera.capability

/** The few facts the home screen shows about this phone. */
data class CapabilitySummary(
    val deviceName: String,
    val androidLabel: String,
    val cameraCount: Int,
    /** The camera Siderea would shoot with by default: the main back camera. */
    val mainCamera: CameraInfo?,
) {
    val manualSensor: Boolean get() = mainCamera?.manualSensor == true
    val raw: Boolean get() = mainCamera?.raw == true

    val longestExposure: String?
        get() = mainCamera?.exposure?.exposureTimeMaxNs?.let { CameraFormat.exposure(it) }

    val isoRange: String?
        get() =
            mainCamera?.exposure?.let { e ->
                if (e.isoMin != null && e.isoMax != null) "${e.isoMin}–${e.isoMax}" else null
            }

    companion object {
        /**
         * The main back camera is the one labelled 1x; failing that, the first readable back camera;
         * failing that, any readable camera. Cameras that errored are never chosen.
         */
        fun from(report: CapabilityReport): CapabilitySummary {
            val readable = report.cameras.filter { it.error == null }
            val back = readable.filter { it.facing == LensFacing.BACK }
            val main =
                back.firstOrNull { it.lens.zoomLabel == "1x" && it.kind != CameraKind.PHYSICAL }
                    ?: back.firstOrNull { it.lens.zoomLabel == "1x" }
                    ?: back.firstOrNull()
                    ?: readable.firstOrNull()
            return CapabilitySummary(
                deviceName = deviceName(report.device),
                androidLabel = "Android ${report.device.androidRelease} (API ${report.device.sdkInt})",
                cameraCount = report.cameras.size,
                mainCamera = main,
            )
        }

        /** "Google Pixel 9 Pro", but "Pixel 9 Pro" is not turned into "Google Google Pixel 9 Pro". */
        internal fun deviceName(device: DeviceInfo): String {
            val maker = device.manufacturer.trim().replaceFirstChar { it.uppercase() }
            val model = device.model.trim()
            return when {
                model.isEmpty() && maker.isEmpty() -> "This phone"
                model.isEmpty() -> maker
                maker.isEmpty() || model.startsWith(maker, ignoreCase = true) -> model
                else -> "$maker $model"
            }
        }
    }
}
