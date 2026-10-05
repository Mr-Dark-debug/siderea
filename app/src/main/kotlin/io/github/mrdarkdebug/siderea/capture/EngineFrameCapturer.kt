package io.github.mrdarkdebug.siderea.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.engine.CameraEngine
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineCapabilities
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineException
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineState
import io.github.mrdarkdebug.siderea.core.camera.engine.RequestPlanner
import io.github.mrdarkdebug.siderea.core.camera.engine.StillRequest
import io.github.mrdarkdebug.siderea.core.capture.session.FrameRecord
import io.github.mrdarkdebug.siderea.core.capture.session.SessionHandle
import io.github.mrdarkdebug.siderea.core.capture.session.SessionLayout
import io.github.mrdarkdebug.siderea.core.capture.timelapse.FrameCapturer
import io.github.mrdarkdebug.siderea.core.capture.timelapse.FrameResult
import io.github.mrdarkdebug.siderea.core.capture.timelapse.MovementDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Takes one frame with the camera engine and writes it into the session folder: JPEG, DNG and a small
 * preview, plus a record of what the sensor *actually* did, from the capture result.
 */
class EngineFrameCapturer(
    private val engine: CameraEngine,
    private val session: SessionHandle,
    private val request: LaunchRequest,
    private val limits: ExposureLimits,
    private val capabilities: () -> EngineCapabilities,
    private val format: CaptureFormat,
    private val movement: MovementDetector?,
    private val reopen: suspend () -> Boolean,
    private val onPreview: (File) -> Unit,
) : FrameCapturer {
    override suspend fun capture(
        index: Int,
        plannedAtMs: Long,
        lockExposure: Boolean,
    ): FrameResult {
        val startedAt = SystemClock.elapsedRealtime()
        val settings = frameSettings(lockExposure)
        val plan =
            RequestPlanner.plan(
                settings,
                settings.shutterNs,
                settings.iso,
                limits,
                capabilities(),
                forPreview = false,
            )
        val flags = if (movement?.movedFromReference() == true) listOf("moved") else emptyList()
        val photo =
            try {
                engine.capture(StillRequest(plan, format, request.jpegOrientation, QUALITY))
            } catch (e: EngineException) {
                return failure(e.userMessage)
            }
        return try {
            val record = withContext(Dispatchers.IO) { write(index, plannedAtMs, startedAt, photo, flags) }
            FrameResult.Captured(record)
        } catch (e: IOException) {
            photo.dng?.delete()
            FrameResult.Failed("Couldn't write the frame (${e.message}). Is the storage full?", fatal = true)
        }
    }

    /** Locked exposure replays the values metered at the start; unlocked lets each frame meter itself. */
    private fun frameSettings(lock: Boolean): CaptureSettings =
        if (lock) {
            request.settings.copy(
                exposureMode = ExposureMode.MANUAL,
                shutterNs = request.lockedShutterNs,
                iso = request.lockedIso,
            )
        } else {
            request.settings.copy(exposureMode = ExposureMode.AUTO)
        }

    private suspend fun failure(message: String): FrameResult {
        // A lost camera ends the session only if one reopen attempt also fails.
        if (engine.state.value is EngineState.Ready) return FrameResult.Failed(message)
        return if (reopen()) {
            FrameResult.Failed(
                "$message (the camera was reopened)",
            )
        } else {
            FrameResult.Failed(message, fatal = true)
        }
    }

    private fun write(
        index: Int,
        plannedAtMs: Long,
        startedAt: Long,
        photo: io.github.mrdarkdebug.siderea.core.camera.engine.CapturedPhoto,
        flags: List<String>,
    ): FrameRecord {
        val name = SessionLayout.frameName(index)
        var jpegBytes = 0L
        var dngBytes = 0L
        photo.jpeg?.let { bytes ->
            val file = session.jpegFile(name)
            file.writeBytes(bytes)
            jpegBytes = bytes.size.toLong()
            writePreview(bytes, session.previewFile(name))?.let(onPreview)
        }
        photo.dng?.let { temp ->
            val target = session.rawFile(name)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            dngBytes = target.length()
        }
        val facts = photo.facts
        return FrameRecord(
            index = index,
            name = name,
            plannedAtMs = plannedAtMs,
            capturedAtEpochMs = System.currentTimeMillis(),
            sensorTimestampNs = facts.sensorTimestampNs,
            exposureNs = facts.exposureNs,
            iso = facts.iso,
            frameDurationNs = facts.frameDurationNs,
            focusDiopters = facts.focusDiopters,
            orientationDegrees = request.jpegOrientation,
            hasJpeg = photo.jpeg != null,
            hasDng = photo.dng != null,
            jpegBytes = jpegBytes,
            dngBytes = dngBytes,
            captureMs = SystemClock.elapsedRealtime() - startedAt,
            flags = flags,
        )
    }

    private fun writePreview(
        jpeg: ByteArray,
        target: File,
    ): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= PREVIEW_WIDTH) sample *= 2
        val bitmap =
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_QUALITY, out)
        bitmap.recycle()
        target.writeBytes(out.toByteArray())
        return target
    }

    private companion object {
        const val QUALITY = 95
        const val PREVIEW_WIDTH = 640
        const val PREVIEW_QUALITY = 80
    }
}
