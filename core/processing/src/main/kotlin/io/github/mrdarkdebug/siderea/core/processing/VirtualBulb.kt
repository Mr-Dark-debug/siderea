@file:Suppress("MagicNumber") // Gamma table size and percentile constants.

package io.github.mrdarkdebug.siderea.core.processing

import kotlin.math.pow

/**
 * "Virtual bulb": a long exposure built from many short ones. A camera can only expose for so long in one go (16 s
 * on a Pixel 10); light adds up, so ten 15 s frames added together look like a 150 s exposure.
 *
 * The addition is done on linear light, not on the encoded JPEG values, because brightness doubles when exposure
 * doubles only in linear light. Sources are JPEGs, so this is an approximation of what a RAW sum would give: the
 * camera's tone curve is undone with a plain 2.2 gamma.
 */
class AdditiveAccumulator(
    val width: Int,
    val height: Int,
) : FrameAccumulator {
    private val sum = FloatArray(width * height * 3)

    /** How many frames have been added. */
    var frames: Int = 0
        private set

    override fun add(
        frame: RgbImage,
        transform: Transform?,
    ) {
        require(frame.width == width && frame.height == height) { "Frame size differs from the first frame" }
        for (i in 0 until width * height) {
            val p = frame.pixels[i]
            sum[i * 3] += TO_LINEAR[(p shr 16) and 0xFF]
            sum[i * 3 + 1] += TO_LINEAR[(p shr 8) and 0xFF]
            sum[i * 3 + 2] += TO_LINEAR[p and 0xFF]
        }
        frames++
    }

    /**
     * The gain that would keep the brightest 0.2 % of the picture from clipping, never above 1. A long exposure of
     * a lit scene is simply over-exposed; this is the "expose to the right" correction.
     */
    fun protectingGain(): Float {
        val step = maxOf(1, sum.size / SAMPLE_TARGET)
        val sample = FloatArray(sum.size / step) { sum[it * step] }
        sample.sort()
        val high = sample[((sample.size - 1) * 0.998).toInt()]
        return if (high > 1f) 1f / high else 1f
    }

    /** The result with every pixel multiplied by [gain] (in linear light) and encoded back to 8-bit. */
    fun toRgbImage(gain: Float): RgbImage {
        val out = IntArray(width * height)
        for (i in out.indices) {
            out[i] =
                RgbImage.pack(encode(sum[i * 3] * gain), encode(sum[i * 3 + 1] * gain), encode(sum[i * 3 + 2] * gain))
        }
        return RgbImage(width, height, out)
    }

    override fun toRgbImage(): RgbImage = toRgbImage(1f)

    private fun encode(linear: Float): Int {
        val v = linear.coerceIn(0f, 1f)
        return FROM_LINEAR[(v * (FROM_LINEAR.size - 1)).toInt()].toInt() and 0xFF
    }

    private companion object {
        const val GAMMA = 2.2f
        const val SAMPLE_TARGET = 200_000
        val TO_LINEAR = FloatArray(256) { (it / 255f).pow(GAMMA) }
        val FROM_LINEAR = ByteArray(4096) { ((it / 4095f).pow(1f / GAMMA) * 255f + 0.5f).toInt().toByte() }
    }
}

/** How the frames of a virtual-bulb session are combined. */
enum class BulbMode(
    val label: String,
    val description: String,
) {
    ADDITIVE("Add light", "Adds the frames like one long exposure: light trails, silky water, bright cities."),
    LIGHTEN(
        "Keep brightest",
        "Each pixel keeps its brightest value: light painting and moving lights without washing out the scene.",
    ),
    AVERAGE("Average", "Averages the frames: removes moving people and traffic, and cleans up noise."),
}

/** Virtual-bulb combining on top of the same streaming accumulators the sky tools use. */
object VirtualBulbProcessor {
    /**
     * Combines every readable frame of [source]. [gain] scales the additive result; null picks the gain that keeps
     * highlights from clipping. Returns null when nothing could be read.
     */
    fun combine(
        source: FrameSource,
        mode: BulbMode,
        gain: Float? = null,
        checkCancelled: () -> Unit = {},
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): RgbImage? =
        when (mode) {
            BulbMode.LIGHTEN -> {
                AstroProcessor.trails(source, null, 1f, checkCancelled, onProgress)
            }

            BulbMode.AVERAGE -> {
                accumulate(source, checkCancelled, onProgress) { w, h -> StackAccumulator(w, h) }?.toRgbImage()
            }

            BulbMode.ADDITIVE -> {
                accumulate(source, checkCancelled, onProgress) { w, h -> AdditiveAccumulator(w, h) }?.let {
                    it.toRgbImage(gain ?: it.protectingGain())
                }
            }
        }

    private fun <T : FrameAccumulator> accumulate(
        source: FrameSource,
        checkCancelled: () -> Unit,
        onProgress: (Int, Int) -> Unit,
        create: (Int, Int) -> T,
    ): T? {
        var accumulator: T? = null
        for (index in 0 until source.count) {
            checkCancelled()
            val frame = source.load(index)
            if (frame != null) {
                val acc = accumulator ?: create(frame.width, frame.height).also { accumulator = it }
                val same =
                    (acc as? AdditiveAccumulator)?.let { it.width == frame.width && it.height == frame.height }
                        ?: (acc as? StackAccumulator)?.let { it.width == frame.width && it.height == frame.height }
                        ?: true
                if (same) acc.add(frame)
            }
            onProgress(index + 1, source.count)
        }
        return accumulator
    }
}
