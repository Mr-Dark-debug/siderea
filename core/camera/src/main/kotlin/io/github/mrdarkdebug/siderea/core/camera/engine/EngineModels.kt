package io.github.mrdarkdebug.siderea.core.camera.engine

import android.view.Surface
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import java.io.File

/** What the engine is doing right now. */
sealed interface EngineState {
    data object Closed : EngineState

    data object Opening : EngineState

    data class Ready(
        val info: ReadyInfo,
    ) : EngineState

    /** The camera can't be used; [message] says why in plain language and what to try. */
    data class Failed(
        val message: String,
    ) : EngineState
}

data class SizeWH(
    val width: Int,
    val height: Int,
)

data class ReadyInfo(
    val lens: Lens,
    val previewSize: SizeWH,
    val jpegSize: SizeWH?,
    val rawSize: SizeWH?,
    /** True when the physical-lens streams could not be created and the logical camera is zoomed instead. */
    val usingLogicalFallback: Boolean,
    val note: String?,
    /** What the open camera accepts, for planning requests. */
    val capabilities: EngineCapabilities,
)

/** Everything needed to open the camera and attach a viewfinder. */
class OpenParams(
    val lens: Lens,
    val format: CaptureFormat,
    val aspect: AspectRatio,
    /** Called on the camera thread with the preview size the engine chose; returns the surface to draw on. */
    val surfaceFor: (width: Int, height: Int) -> Surface?,
)

/** Live values reported by the camera for the latest preview frames. */
data class FrameInfo(
    val exposureNs: Long?,
    val iso: Int?,
    val focusDiopters: Float?,
    val afState: Int?,
    val aeState: Int?,
    /** The nearest focus distance seen while autofocusing, in diopters, if the camera never reported one. */
    val observedNearDiopters: Float?,
)

/** A photo request: what the sensor does and what to produce. */
class StillRequest(
    val plan: RequestPlan,
    val format: CaptureFormat,
    /** From `OrientationMath.jpegOrientation`. */
    val jpegOrientation: Int,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
) {
    companion object {
        const val DEFAULT_JPEG_QUALITY = 95
    }
}

/** What the sensor actually used for a finished capture (from `CaptureResult`, not the request). */
data class CaptureFacts(
    val exposureNs: Long?,
    val iso: Int?,
    val focusDiopters: Float?,
    val frameDurationNs: Long?,
    val sensorTimestampNs: Long?,
    val colorTemperatureHint: String?,
)

/** A finished photo. The DNG, if any, is a temporary file the caller must move or delete. */
class CapturedPhoto(
    val jpeg: ByteArray?,
    val dng: File?,
    val width: Int,
    val height: Int,
    val facts: CaptureFacts,
)

/** Progress signals for a capture in flight. */
sealed interface CaptureEvent {
    /** The sensor started integrating; it will take about [durationNs]. */
    data class Exposing(
        val durationNs: Long,
        val startedAtElapsedMs: Long,
    ) : CaptureEvent

    /** Frame is exposed; reading it out and writing files. */
    data object Processing : CaptureEvent
}

/** A failure with wording fit to show a user. */
class EngineException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)
