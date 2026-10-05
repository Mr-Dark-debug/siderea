package io.github.mrdarkdebug.siderea.core.camera.analysis

import kotlin.math.abs

/** Result of analysing one small preview frame. Masks are row-major, `width * height` long. */
class FrameAnalysis(
    val width: Int,
    val height: Int,
    /** 256 bins of the display luma. */
    val histogram: IntArray,
    /** Mean luma, 0 to 255. */
    val meanLuma: Float,
    /** Fraction of pixels at or above the clipping threshold (0 to 1). */
    val clippedHigh: Float,
    /** Fraction of pixels at or below the crushed-black threshold (0 to 1). */
    val clippedLow: Float,
    /** In-focus edges (focus peaking), or null when peaking is off. */
    val peaking: BooleanArray?,
    /** Over-exposed pixels, or null when zebras are off. */
    val zebra: BooleanArray?,
)

/**
 * Viewfinder aids computed on a downscaled copy of the preview, on a background thread.
 * All of it is plain array maths so it can be tested without a camera.
 */
object LumaAnalysis {
    const val BINS = 256
    const val CLIP_HIGH = 250
    const val CLIP_LOW = 5

    /** Pixels at or above this luma get zebra stripes (about 92 % of full scale). */
    const val ZEBRA_THRESHOLD = 235

    /** Gradient (out of 510) above which an edge counts as in focus. */
    const val DEFAULT_PEAKING_THRESHOLD = 60

    fun analyze(
        argb: IntArray,
        width: Int,
        height: Int,
        peakingThreshold: Int? = null,
        zebraThreshold: Int? = null,
    ): FrameAnalysis {
        require(argb.size == width * height) { "pixel count ${argb.size} does not match ${width}x$height" }
        val luma = luma(argb)
        val histogram = histogram(luma)
        val total = luma.size.coerceAtLeast(1)
        var sum = 0L
        for (bin in histogram.indices) sum += bin.toLong() * histogram[bin]
        var high = 0
        for (bin in CLIP_HIGH until BINS) high += histogram[bin]
        var low = 0
        for (bin in 0..CLIP_LOW) low += histogram[bin]
        return FrameAnalysis(
            width = width,
            height = height,
            histogram = histogram,
            meanLuma = sum.toFloat() / total,
            clippedHigh = high.toFloat() / total,
            clippedLow = low.toFloat() / total,
            peaking = peakingThreshold?.let { peaking(luma, width, height, it) },
            zebra = zebraThreshold?.let { zebra(luma, it) },
        )
    }

    /** Rec. 709 luma of packed ARGB pixels, 0 to 255. */
    fun luma(argb: IntArray): IntArray =
        IntArray(argb.size) { i ->
            val p = argb[i]
            val r = (p shr RED_SHIFT) and BYTE
            val g = (p shr GREEN_SHIFT) and BYTE
            val b = p and BYTE
            (LUMA_R * r + LUMA_G * g + LUMA_B * b) shr LUMA_SHIFT
        }

    fun histogram(luma: IntArray): IntArray {
        val bins = IntArray(BINS)
        for (value in luma) bins[value.coerceIn(0, BINS - 1)]++
        return bins
    }

    /** Marks pixels whose local luma gradient exceeds [threshold]: sharp, in-focus detail. */
    fun peaking(
        luma: IntArray,
        width: Int,
        height: Int,
        threshold: Int,
    ): BooleanArray {
        val mask = BooleanArray(luma.size)
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val gx = abs(luma[row + x + 1] - luma[row + x - 1])
                val gy = abs(luma[row + width + x] - luma[row - width + x])
                mask[row + x] = gx + gy > threshold
            }
        }
        return mask
    }

    fun zebra(
        luma: IntArray,
        threshold: Int,
    ): BooleanArray = BooleanArray(luma.size) { luma[it] >= threshold }

    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE = 0xFF
    private const val LUMA_R = 54
    private const val LUMA_G = 183
    private const val LUMA_B = 19
    private const val LUMA_SHIFT = 8
}
