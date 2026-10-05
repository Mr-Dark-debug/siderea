package io.github.mrdarkdebug.siderea.core.capture.timelapse

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig
import kotlin.math.ceil

/** How long a capture takes beyond its exposure, and whether that number was measured or guessed. */
data class OverheadEstimate(
    val overheadMs: Long,
    val measured: Boolean,
    val samples: Int,
)

/**
 * Learns the real per-frame overhead on this phone (sensor read-out, JPEG encoding, DNG writing, saving to
 * disk) from captures that actually happened, and falls back to a clearly-labelled estimate until then.
 */
class OverheadTracker {
    private val samplesMs = ArrayDeque<Long>()

    /** Records one finished capture: the total time it took and the exposure it contained. */
    fun record(
        totalCaptureMs: Long,
        exposureNs: Long,
    ) {
        val overhead = (totalCaptureMs - exposureNs / NS_PER_MS).coerceAtLeast(0)
        samplesMs.addLast(overhead)
        while (samplesMs.size > WINDOW) samplesMs.removeFirst()
    }

    /**
     * The recent worst case plus a margin. A timelapse that sometimes misses its slot is worse than one
     * that runs a little slower than it could, so this errs high.
     */
    fun estimate(format: CaptureFormat): OverheadEstimate {
        if (samplesMs.isEmpty()) return OverheadEstimate(defaultOverheadMs(format), measured = false, samples = 0)
        val worst = samplesMs.max()
        return OverheadEstimate((worst * MARGIN).toLong(), measured = true, samples = samplesMs.size)
    }

    private fun defaultOverheadMs(format: CaptureFormat): Long =
        when (format) {
            CaptureFormat.JPEG -> DEFAULT_JPEG_MS
            CaptureFormat.RAW -> DEFAULT_RAW_MS
            CaptureFormat.RAW_JPEG -> DEFAULT_RAW_JPEG_MS
        }

    private companion object {
        const val WINDOW = 8
        const val MARGIN = 1.15
        const val NS_PER_MS = 1_000_000L
        const val DEFAULT_JPEG_MS = 900L
        const val DEFAULT_RAW_MS = 2_500L
        const val DEFAULT_RAW_JPEG_MS = 3_000L
    }
}

/** The verdict on an interval: fine, or what the real minimum is. */
data class IntervalCheck(
    val ok: Boolean,
    val minimumMs: Long,
    val measured: Boolean,
    val message: String,
)

/** Numbers shown while setting up a timelapse. Pure functions: nothing here touches a device. */
object IntervalMath {
    private const val NS_PER_MS = 1_000_000L
    private const val SAFETY_MS = 250L
    private const val MS_PER_SECOND = 1_000.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val MS_PER_HOUR = 3_600_000.0

    /** Presets offered in the UI, in milliseconds. */
    val presetsMs: List<Long> = listOf(1_000, 2_000, 5_000, 10_000, 30_000, 60_000, 300_000, 1_800_000)

    /** The shortest interval that can work: exposure + measured overhead + a small safety margin. */
    fun minimumIntervalMs(
        exposureNs: Long,
        overhead: OverheadEstimate,
    ): Long = ceil(exposureNs / NS_PER_MS.toDouble()).toLong() + overhead.overheadMs + SAFETY_MS

    /** Explains in human terms whether [intervalMs] is long enough, with the real minimum. */
    fun check(
        intervalMs: Long,
        exposureNs: Long,
        overhead: OverheadEstimate,
    ): IntervalCheck {
        val minimum = minimumIntervalMs(exposureNs, overhead)
        val source = if (overhead.measured) "measured" else "estimated, Siderea will measure it on the first frames"
        return if (intervalMs >= minimum) {
            IntervalCheck(true, minimum, overhead.measured, "Minimum interval: ${formatSeconds(minimum)} ($source)")
        } else {
            IntervalCheck(
                ok = false,
                minimumMs = minimum,
                measured = overhead.measured,
                message =
                    "This interval is too short. Minimum interval: ${formatSeconds(minimum)} ($source). " +
                        "Raise the interval or shorten the exposure.",
            )
        }
    }

    fun expectedFrames(config: TimelapseConfig): Int? = config.plannedFrames

    /** Length of the finished video, in seconds, at [fps]. */
    fun outputSeconds(
        frames: Int,
        fps: Int,
    ): Double = frames.toDouble() / fps.coerceAtLeast(1)

    fun storageBytes(
        frames: Int,
        bytesPerFrame: Long,
    ): Long = frames.toLong() * bytesPerFrame

    /** Typical size of one frame, for estimates before any frame has been taken. */
    fun typicalFrameBytes(
        format: CaptureFormat,
        megapixels: Float,
    ): Long {
        val jpeg = (megapixels * JPEG_BYTES_PER_MP).toLong()
        val dng = (megapixels * DNG_BYTES_PER_MP).toLong()
        return when (format) {
            CaptureFormat.JPEG -> jpeg
            CaptureFormat.RAW -> dng
            CaptureFormat.RAW_JPEG -> jpeg + dng
        }
    }

    /**
     * Rough battery use for a session, as a percentage of a full battery, from how busy the camera is.
     * A model, not a measurement: the running session replaces it with the real drain.
     */
    fun batteryPercent(
        durationMs: Long,
        intervalMs: Long,
        exposureNs: Long,
        overheadMs: Long,
    ): Double {
        val busyMs = (exposureNs / NS_PER_MS + overheadMs).toDouble()
        val dutyCycle = (busyMs / intervalMs.toDouble()).coerceIn(0.0, 1.0)
        val perHour = IDLE_PERCENT_PER_HOUR + ACTIVE_PERCENT_PER_HOUR * dutyCycle
        return perHour * durationMs / MS_PER_HOUR
    }

    /** Session length for a config: the duration, or frames x interval; null when it runs until stopped. */
    fun sessionDurationMs(config: TimelapseConfig): Long? =
        when (config.stop) {
            StopCondition.DURATION -> config.durationMs
            StopCondition.FRAME_COUNT -> config.frameCount?.let { (it - 1).coerceAtLeast(0) * config.intervalMs }
            StopCondition.UNTIL_STOPPED -> null
        }

    fun formatSeconds(ms: Long): String {
        val seconds = ms / MS_PER_SECOND
        return if (seconds < SECONDS_PER_MINUTE) {
            "%.1f s".format(java.util.Locale.ROOT, seconds)
        } else {
            val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
            "$minutes min ${(seconds - minutes * SECONDS_PER_MINUTE).toInt()} s"
        }
    }

    private const val JPEG_BYTES_PER_MP = 400_000f
    private const val DNG_BYTES_PER_MP = 2_000_000f
    private const val IDLE_PERCENT_PER_HOUR = 4.0
    private const val ACTIVE_PERCENT_PER_HOUR = 22.0
}
