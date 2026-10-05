package io.github.mrdarkdebug.siderea.core.capture.session

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import kotlinx.serialization.Serializable

@Serializable
enum class SessionKind(
    val folderLabel: String,
    val title: String,
) {
    TIMELAPSE("Timelapse", "Timelapse"),
    ASTRO("Astro", "Astro"),
    LONG_EXPOSURE("LongExposure", "Long exposure"),
}

@Serializable
enum class SessionStatus {
    /** The session is capturing now (or was when the app was killed: see [INTERRUPTED]). */
    RUNNING,
    COMPLETED,

    /** The user stopped it. */
    STOPPED,

    /** The process died or the phone shut down mid-session; the frames captured so far are intact. */
    INTERRUPTED,

    /** Ended early because of a problem such as a lost camera, full storage or overheating. */
    FAILED,
}

@Serializable
enum class StopCondition { FRAME_COUNT, DURATION, UNTIL_STOPPED }

/** What the intervalometer was asked to do. */
@Serializable
data class TimelapseConfig(
    val intervalMs: Long,
    val stop: StopCondition,
    val frameCount: Int? = null,
    val durationMs: Long? = null,
    /** Exposure is fixed for the whole session (recommended); otherwise each frame meters itself. */
    val lockExposure: Boolean = true,
    val outputFps: Int = DEFAULT_FPS,
    /** Slow the interval or pause when the phone overheats, instead of ending the session. */
    val adaptToHeat: Boolean = true,
) {
    /** Frames the session is expected to produce, or null when it runs until stopped. */
    val plannedFrames: Int?
        get() =
            when (stop) {
                StopCondition.FRAME_COUNT -> frameCount
                StopCondition.DURATION -> durationMs?.let { (it / intervalMs).toInt() + 1 }
                StopCondition.UNTIL_STOPPED -> null
            }

    companion object {
        const val DEFAULT_FPS = 30
    }
}

/**
 * What the sensor actually did for one frame, taken from the capture result. These are the values export
 * and analysis use, never the requested ones.
 */
@Serializable
data class FrameRecord(
    val index: Int,
    /** File stem shared by raw/, jpeg/ and previews/, e.g. `IMG_000001`. */
    val name: String,
    /** When this frame was scheduled, in milliseconds after the session started. */
    val plannedAtMs: Long,
    /** When it really finished, wall-clock milliseconds. */
    val capturedAtEpochMs: Long,
    val sensorTimestampNs: Long? = null,
    val exposureNs: Long? = null,
    val iso: Int? = null,
    val frameDurationNs: Long? = null,
    val focusDiopters: Float? = null,
    val orientationDegrees: Int = 0,
    val hasJpeg: Boolean = false,
    val hasDng: Boolean = false,
    val jpegBytes: Long = 0,
    val dngBytes: Long = 0,
    /** How long the capture took from request to files on disk. Feeds the overhead model. */
    val captureMs: Long = 0,
    /** Gyroscope, thermal or other marks, e.g. `moved`. The user can exclude flagged frames. */
    val flags: List<String> = emptyList(),
    val error: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class AppSnapshot(
    val name: String,
    val versionName: String,
    val versionCode: Long,
)

@Serializable
data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
)

/** Which camera and lens took the frames. */
@Serializable
data class CameraSnapshot(
    val lensKey: String,
    val openId: String,
    val physicalId: String?,
    val zoomLabel: String,
    val facing: String,
)

/** The settings the user asked for. Per-frame truth lives in [FrameRecord]. */
@Serializable
data class RequestedCapture(
    val settings: CaptureSettings,
    val aspect: String,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
) {
    companion object {
        const val DEFAULT_JPEG_QUALITY = 95
    }
}

/** A noteworthy thing that happened during a session, in plain language. */
@Serializable
data class SessionEvent(
    val atEpochMs: Long,
    val text: String,
)

/** The contents of `session.json`. */
@Serializable
data class SessionManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
    val name: String,
    val kind: SessionKind,
    val status: SessionStatus,
    val createdAtEpochMs: Long,
    val finishedAtEpochMs: Long? = null,
    val app: AppSnapshot,
    val device: DeviceSnapshot,
    val camera: CameraSnapshot,
    val requested: RequestedCapture,
    val timelapse: TimelapseConfig? = null,
    val events: List<SessionEvent> = emptyList(),
    val frames: List<FrameRecord> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** A cheap, list-friendly view of a session. */
data class SessionSummary(
    val id: String,
    val name: String,
    val kind: SessionKind,
    val status: SessionStatus,
    val createdAtEpochMs: Long,
    val frameCount: Int,
    val plannedFrames: Int?,
    /** First to last frame, in milliseconds. */
    val spanMs: Long,
    val sizeBytes: Long,
    val folderName: String,
)
