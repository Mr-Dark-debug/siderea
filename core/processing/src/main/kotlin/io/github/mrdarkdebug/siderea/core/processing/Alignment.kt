@file:Suppress("MagicNumber") // Tolerances of the star matcher are tuning values documented where they are used.

package io.github.mrdarkdebug.siderea.core.processing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A similarity transform (shift, rotation, and a scale near 1) taking a frame's coordinates into the reference
 * frame's: `x' = a*x - b*y + tx`, `y' = b*x + a*y + ty`.
 */
data class Transform(
    val a: Double,
    val b: Double,
    val tx: Double,
    val ty: Double,
) {
    val scale: Double get() = hypot(a, b)

    /** Rotation in degrees, counter-clockwise on screen coordinates. */
    val rotationDegrees: Double get() = Math.toDegrees(atan2(b, a))

    val isIdentity: Boolean get() = a == 1.0 && b == 0.0 && tx == 0.0 && ty == 0.0

    fun mapX(
        x: Double,
        y: Double,
    ): Double = a * x - b * y + tx

    fun mapY(
        x: Double,
        y: Double,
    ): Double = b * x + a * y + ty

    companion object {
        val IDENTITY = Transform(1.0, 0.0, 0.0, 0.0)
    }
}

/** How well a frame matched the reference. */
data class AlignResult(
    val transform: Transform,
    val inliers: Int,
    /** Root-mean-square distance in pixels between matched stars after alignment. */
    val rmsError: Double,
)

/**
 * Lines a frame up with a reference by matching their star patterns, so it works whatever the sky is doing: it
 * tries a transform from every pair of stars in the reference against every comparable pair in the frame and
 * keeps the one that explains the most stars, then refines it on all of them.
 */
object Alignment {
    private const val POOL = 30
    private const val TOLERANCE = 2.5
    private const val MIN_BASELINE = 30.0
    private const val MAX_SCALE_ERROR = 0.02
    private const val MIN_INLIERS = 4
    private const val MIN_FRACTION = 0.4

    /** Returns how to move [frame] onto [reference], or null when the star patterns do not agree. */
    fun estimate(
        reference: List<Star>,
        frame: List<Star>,
    ): AlignResult? {
        val refs = reference.take(POOL)
        val stars = frame.take(POOL)
        if (refs.size < MIN_INLIERS || stars.size < MIN_INLIERS) return null

        var best: Transform? = null
        var bestInliers = 0
        for (i in refs.indices) {
            for (j in i + 1 until refs.size) {
                val dr = hypot(refs[j].x - refs[i].x, refs[j].y - refs[i].y)
                if (dr < MIN_BASELINE) continue
                for (k in stars.indices) {
                    for (l in stars.indices) {
                        if (k == l) continue
                        val df = hypot(stars[l].x - stars[k].x, stars[l].y - stars[k].y)
                        if (abs(df - dr) > TOLERANCE) continue
                        val candidate = fromPairs(stars[k], stars[l], refs[i], refs[j]) ?: continue
                        val inliers = countInliers(candidate, refs, stars)
                        if (inliers > bestInliers) {
                            bestInliers = inliers
                            best = candidate
                        }
                    }
                }
            }
        }
        val hypothesis = best ?: return null
        if (bestInliers < maxOf(MIN_INLIERS, (MIN_FRACTION * minOf(refs.size, stars.size)).toInt())) return null
        return refine(hypothesis, refs, stars)
    }

    /** The transform taking p1 to q1 and p2 to q2, or null if it would change scale too much. */
    private fun fromPairs(
        p1: Star,
        p2: Star,
        q1: Star,
        q2: Star,
    ): Transform? {
        val pdx = p2.x - p1.x
        val pdy = p2.y - p1.y
        val qdx = q2.x - q1.x
        val qdy = q2.y - q1.y
        val pn = pdx * pdx + pdy * pdy
        if (pn == 0.0) return null
        // Complex division (qd / pd) gives scale and rotation as a + ib.
        val a = (qdx * pdx + qdy * pdy) / pn
        val b = (qdy * pdx - qdx * pdy) / pn
        if (abs(hypot(a, b) - 1.0) > MAX_SCALE_ERROR) return null
        return Transform(a, b, q1.x - (a * p1.x - b * p1.y), q1.y - (b * p1.x + a * p1.y))
    }

    private fun countInliers(
        t: Transform,
        refs: List<Star>,
        stars: List<Star>,
    ): Int {
        var count = 0
        for (s in stars) {
            val x = t.mapX(s.x, s.y)
            val y = t.mapY(s.x, s.y)
            if (refs.any { hypot(it.x - x, it.y - y) <= TOLERANCE }) count++
        }
        return count
    }

    /** Least-squares similarity over every matched pair (closed form, complex arithmetic). */
    private fun refine(
        start: Transform,
        refs: List<Star>,
        stars: List<Star>,
    ): AlignResult? {
        val pairs = ArrayList<Pair<Star, Star>>()
        for (s in stars) {
            val x = start.mapX(s.x, s.y)
            val y = start.mapY(s.x, s.y)
            val nearest = refs.minBy { hypot(it.x - x, it.y - y) }
            if (hypot(nearest.x - x, nearest.y - y) <= TOLERANCE) pairs += s to nearest
        }
        if (pairs.size < MIN_INLIERS) return null
        val n = pairs.size.toDouble()
        val px = pairs.sumOf { it.first.x } / n
        val py = pairs.sumOf { it.first.y } / n
        val qx = pairs.sumOf { it.second.x } / n
        val qy = pairs.sumOf { it.second.y } / n
        var num1 = 0.0
        var num2 = 0.0
        var den = 0.0
        for ((p, q) in pairs) {
            val ux = p.x - px
            val uy = p.y - py
            val vx = q.x - qx
            val vy = q.y - qy
            num1 += ux * vx + uy * vy
            num2 += ux * vy - uy * vx
            den += ux * ux + uy * uy
        }
        if (den == 0.0) return null
        val a = num1 / den
        val b = num2 / den
        val t = Transform(a, b, qx - (a * px - b * py), qy - (b * px + a * py))
        var square = 0.0
        for ((p, q) in pairs) {
            val dx = t.mapX(p.x, p.y) - q.x
            val dy = t.mapY(p.x, p.y) - q.y
            square += dx * dx + dy * dy
        }
        return AlignResult(t, pairs.size, kotlin.math.sqrt(square / n))
    }
}
