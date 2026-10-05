package io.github.mrdarkdebug.siderea.core.camera.engine

import io.github.mrdarkdebug.siderea.core.camera.capability.SizeInfo
import kotlin.math.abs

/** Frame shape of the photos Siderea shoots. */
enum class AspectRatio(
    val label: String,
    val width: Int,
    val height: Int,
) {
    FOUR_THREE("4:3", 4, 3),
    SIXTEEN_NINE("16:9", 16, 9),
    ;

    val value: Float get() = width.toFloat() / height

    fun matches(
        size: SizeInfo,
        tolerance: Float = TOLERANCE,
    ): Boolean = abs(size.width.toFloat() / size.height - value) <= tolerance

    private companion object {
        const val TOLERANCE = 0.03f
    }
}

/** Picks output sizes from what a lens lists. */
object SensorSizing {
    /** Largest capture size with the wanted shape, or the largest of all when none has it. */
    fun capture(
        sizes: List<SizeInfo>,
        aspect: AspectRatio,
    ): SizeInfo? {
        val matching = sizes.filter(aspect::matches)
        return (matching.ifEmpty { sizes }).maxByOrNull { it.width.toLong() * it.height }
    }

    /**
     * A preview no bigger than [maxWidth] x [maxHeight] (landscape sensor orientation) with the wanted
     * shape. Smaller previews keep the pipeline light so long exposures and RAW capture have headroom.
     */
    fun preview(
        sizes: List<SizeInfo>,
        aspect: AspectRatio,
        maxWidth: Int = DEFAULT_MAX_PREVIEW_WIDTH,
        maxHeight: Int = DEFAULT_MAX_PREVIEW_HEIGHT,
    ): SizeInfo? {
        val fitting = sizes.filter { it.width <= maxWidth && it.height <= maxHeight }
        val matching = fitting.filter(aspect::matches)
        return (matching.ifEmpty { fitting.ifEmpty { sizes } }).maxByOrNull { it.width.toLong() * it.height }
    }

    /**
     * Maps a tap on the upright portrait preview, as fractions (0..1) of its width and height, to
     * fractions of the sensor's active array, which is stored in the sensor's own landscape orientation.
     */
    fun tapToSensor(
        fractionX: Float,
        fractionY: Float,
        sensorOrientation: Int,
    ): Pair<Float, Float> =
        when (((sensorOrientation % FULL) + FULL) % FULL) {
            QUARTER -> fractionY to 1f - fractionX
            HALF -> 1f - fractionX to 1f - fractionY
            THREE_QUARTERS -> 1f - fractionY to fractionX
            else -> fractionX to fractionY
        }

    private const val DEFAULT_MAX_PREVIEW_WIDTH = 1920
    private const val DEFAULT_MAX_PREVIEW_HEIGHT = 1440
    private const val FULL = 360
    private const val QUARTER = 90
    private const val HALF = 180
    private const val THREE_QUARTERS = 270
}
