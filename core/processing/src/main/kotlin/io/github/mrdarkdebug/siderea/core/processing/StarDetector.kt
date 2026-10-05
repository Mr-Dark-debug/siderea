@file:Suppress("MagicNumber") // Thresholds and window sizes of the detector are tuning values, named where they matter.

package io.github.mrdarkdebug.siderea.core.processing

import kotlin.math.sqrt

/** A star found in a frame: sub-pixel centre and total brightness above the background. */
data class Star(
    val x: Double,
    val y: Double,
    val flux: Double,
)

/**
 * Finds stars as bright local maxima well above the sky background, then measures each one's centre of mass.
 * It is deliberately simple: it only needs to be good enough to line frames up, and it reports how many it found
 * so a frame with too few can be rejected instead of stacked badly.
 */
object StarDetector {
    private const val SIGMA = 5.0f
    private const val WING_SIGMA = 1.5f
    private const val MIN_SEPARATION = 6
    private const val CENTROID_RADIUS = 3
    private const val MIN_PEAK_ABOVE_BACKGROUND = 12f

    /** Up to [maxStars] brightest stars, brightest first. */
    fun detect(
        image: RgbImage,
        maxStars: Int = 40,
    ): List<Star> {
        val w = image.width
        val h = image.height
        val luma = FloatArray(w * h) { image.luma(it) }
        val (mean, deviation) = backgroundStatistics(luma)
        val threshold = maxOf(mean + SIGMA * deviation, mean + MIN_PEAK_ABOVE_BACKGROUND)

        val candidates = ArrayList<Star>()
        for (y in CENTROID_RADIUS until h - CENTROID_RADIUS) {
            for (x in CENTROID_RADIUS until w - CENTROID_RADIUS) {
                val value = luma[y * w + x]
                if (value >= threshold && isLocalMaximum(luma, w, x, y) &&
                    hasWings(luma, w, x, y, mean + WING_SIGMA * deviation)
                ) {
                    candidates += centroid(luma, w, x, y, mean)
                }
            }
        }
        return separate(candidates.sortedByDescending { it.flux }, maxStars)
    }

    /** Median-ish mean and spread of the sky, ignoring the brightest tail so stars don't raise their own threshold. */
    private fun backgroundStatistics(luma: FloatArray): Pair<Float, Float> {
        val step = maxOf(1, luma.size / SAMPLE_TARGET)
        val sample = FloatArray(luma.size / step) { luma[it * step] }
        sample.sort()
        val keep = (sample.size * KEEP_FRACTION).toInt().coerceAtLeast(1)
        var sum = 0.0
        for (i in 0 until keep) sum += sample[i]
        val mean = (sum / keep).toFloat()
        var square = 0.0
        for (i in 0 until keep) square += (sample[i] - mean) * (sample[i] - mean)
        return mean to sqrt(square / keep).toFloat().coerceAtLeast(MIN_DEVIATION)
    }

    /**
     * A star's light spreads over its neighbours; a hot pixel or a noise spike does not. Requiring the eight
     * neighbours to be raised too keeps single-pixel defects out of the star list.
     */
    private fun hasWings(
        luma: FloatArray,
        w: Int,
        x: Int,
        y: Int,
        level: Float,
    ): Boolean {
        var sum = 0f
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx != 0 || dy != 0) sum += luma[(y + dy) * w + x + dx]
            }
        }
        return sum / 8f >= level
    }

    private fun isLocalMaximum(
        luma: FloatArray,
        w: Int,
        x: Int,
        y: Int,
    ): Boolean {
        val value = luma[y * w + x]
        for (dy in -1..1) {
            for (dx in -1..1) {
                if ((dx != 0 || dy != 0) && luma[(y + dy) * w + x + dx] > value) return false
            }
        }
        // Break ties between equal neighbours so a flat-topped star is counted once.
        return luma[y * w + x - 1] < value || luma[(y - 1) * w + x] < value
    }

    private fun centroid(
        luma: FloatArray,
        w: Int,
        cx: Int,
        cy: Int,
        background: Float,
    ): Star {
        var total = 0.0
        var sx = 0.0
        var sy = 0.0
        for (dy in -CENTROID_RADIUS..CENTROID_RADIUS) {
            for (dx in -CENTROID_RADIUS..CENTROID_RADIUS) {
                val v = (luma[(cy + dy) * w + cx + dx] - background).coerceAtLeast(0f).toDouble()
                total += v
                sx += v * (cx + dx)
                sy += v * (cy + dy)
            }
        }
        return if (total <= 0.0) Star(cx.toDouble(), cy.toDouble(), 0.0) else Star(sx / total, sy / total, total)
    }

    /** Keeps the brightest star of any group closer than [MIN_SEPARATION] pixels. */
    private fun separate(
        sorted: List<Star>,
        limit: Int,
    ): List<Star> {
        val kept = ArrayList<Star>()
        for (star in sorted) {
            if (kept.size >= limit) break
            val clear =
                kept.none {
                    val dx = it.x - star.x
                    val dy = it.y - star.y
                    dx * dx + dy * dy < MIN_SEPARATION * MIN_SEPARATION
                }
            if (clear) kept += star
        }
        return kept
    }

    private const val SAMPLE_TARGET = 200_000
    private const val KEEP_FRACTION = 0.9f
    private const val MIN_DEVIATION = 1.5f
}
