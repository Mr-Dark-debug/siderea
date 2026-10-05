package io.github.mrdarkdebug.siderea.core.camera.control

import io.github.mrdarkdebug.siderea.core.camera.capability.CameraInfo
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityJson
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityReport

/** The real Pixel 10 report, shared by the control tests. */
internal object Pixel10Fixture {
    val report: CapabilityReport by lazy {
        CapabilityJson.decode(
            checkNotNull(
                Pixel10Fixture::class.java.getResourceAsStream("/google-pixel-10-android-17.json"),
            ) { "missing fixture" }
                .bufferedReader()
                .use { it.readText() },
        )
    }

    fun camera(id: String): CameraInfo = report.cameras.first { it.id == id }

    fun limits(id: String): ExposureLimits = ExposureLimits.from(camera(id), report.device.sdkInt)
}
