package io.github.mrdarkdebug.siderea.core.processing

import java.util.Random
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Renders fake star fields so alignment and stacking can be checked against known truth. */
class SyntheticSky(
    val width: Int = 480,
    val height: Int = 360,
    starCount: Int = 45,
    seed: Long = 7,
) {
    data class Spec(
        val x: Double,
        val y: Double,
        val peak: Double,
    )

    val stars: List<Spec>

    init {
        val rng = Random(seed)
        stars =
            List(starCount) {
                Spec(
                    30 + rng.nextDouble() * (width - 60),
                    30 + rng.nextDouble() * (height - 60),
                    80 + rng.nextDouble() * 170,
                )
            }
    }

    /**
     * One frame: the sky shifted by ([dx], [dy]) and rotated by [degrees] about the centre, with a flat
     * background of [background], gaussian [noise] and optional [hotPixels] (x, y, value).
     */
    fun frame(
        dx: Double = 0.0,
        dy: Double = 0.0,
        degrees: Double = 0.0,
        noise: Double = 0.0,
        background: Double = 20.0,
        seed: Long = 1,
        hotPixels: List<Triple<Int, Int, Int>> = emptyList(),
    ): RgbImage {
        val rng = Random(seed)
        val luma = DoubleArray(width * height) { background }
        val cx = width / 2.0
        val cy = height / 2.0
        val rad = Math.toRadians(degrees)
        for (s in stars) {
            val x = cos(rad) * (s.x - cx) - sin(rad) * (s.y - cy) + cx + dx
            val y = sin(rad) * (s.x - cx) + cos(rad) * (s.y - cy) + cy + dy
            splat(luma, x, y, s.peak)
        }
        val out = IntArray(width * height)
        for (i in out.indices) {
            val v = (luma[i] + rng.nextGaussian() * noise + 0.5).toInt().coerceIn(0, 255)
            out[i] = RgbImage.pack(v, v, v)
        }
        for ((x, y, v) in hotPixels) out[y * width + x] = RgbImage.pack(v, v, v)
        return RgbImage(width, height, out)
    }

    private fun splat(
        luma: DoubleArray,
        x: Double,
        y: Double,
        peak: Double,
    ) {
        val sigma = 1.3
        val r = 5
        for (py in (y.toInt() - r)..(y.toInt() + r)) {
            for (px in (x.toInt() - r)..(x.toInt() + r)) {
                if (px < 0 || py < 0 || px >= width || py >= height) continue
                val d2 = (px - x) * (px - x) + (py - y) * (py - y)
                luma[py * width + px] += peak * exp(-d2 / (2 * sigma * sigma))
            }
        }
    }
}

/** A frame source over prepared images; a null entry plays the part of an unreadable file. */
class ListSource(
    private val frames: List<RgbImage?>,
) : FrameSource {
    override val count: Int get() = frames.size

    /** Hands out a copy each time, as a real decoder would, because dark subtraction edits the image in place. */
    override fun load(index: Int): RgbImage? = frames[index]?.let { RgbImage(it.width, it.height, it.pixels.copyOf()) }
}
