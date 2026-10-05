package io.github.mrdarkdebug.siderea.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DeflickerTest {
    private fun corrected(
        luma: DoubleArray,
        window: Int,
    ): DoubleArray {
        val gains = Deflicker.gains(luma, window)
        return DoubleArray(luma.size) { luma[it] * gains[it] }
    }

    private fun spread(values: DoubleArray): Double = (values.max() - values.min()) / values.average()

    @Test
    fun noWindowMeansNoChange() {
        val gains = Deflicker.gains(doubleArrayOf(0.2, 0.6, 0.2), 0)
        gains.forEach { assertEquals(1.0, it, 0.0) }
    }

    @Test
    fun aSingleFrameIsLeftAlone() {
        assertEquals(1.0, Deflicker.gains(doubleArrayOf(0.4), 5)[0], 0.0)
    }

    @Test
    fun alternatingFlickerIsFlattened() {
        val flicker = DoubleArray(60) { if (it % 2 == 0) 0.40 else 0.48 }
        val before = spread(flicker)
        val after = spread(corrected(flicker, 4))
        assertTrue("flicker should shrink a lot: $before -> $after", after < before / 3)
    }

    @Test
    fun aSlowRampSurvives() {
        val ramp = DoubleArray(100) { 0.2 + it * 0.004 }
        val out = corrected(ramp, 5)
        // Interior frames stay within 1.5% of where they were: the trend is not flattened.
        for (i in 6 until 94) assertTrue(abs(out[i] - ramp[i]) / ramp[i] < 0.015)
        assertTrue("still rises", out[90] > out[10] * 1.5)
    }

    @Test
    fun anIsolatedBrightFrameIsPulledDown() {
        val luma = DoubleArray(21) { 0.30 }
        luma[10] = 0.45
        val out = corrected(luma, 5)
        assertTrue(out[10] < 0.34)
    }

    @Test
    fun gainsAreLimitedSoOneBadFrameCannotBlowOut() {
        val luma = DoubleArray(21) { 0.30 }
        luma[10] = 0.0
        val gains = Deflicker.gains(luma, 5)
        assertTrue(gains.all { it in 0.6..1.6 })
    }

    @Test
    fun meanLumaOfKnownColours() {
        assertEquals(0.0, Deflicker.meanLuma(intArrayOf(0xFF000000.toInt())), 1e-9)
        assertEquals(1.0, Deflicker.meanLuma(intArrayOf(0xFFFFFFFF.toInt())), 1e-9)
        assertEquals(0.2126, Deflicker.meanLuma(intArrayOf(0xFFFF0000.toInt())), 1e-9)
        assertEquals(0.7152, Deflicker.meanLuma(intArrayOf(0xFF00FF00.toInt())), 1e-9)
        assertEquals(0.5, Deflicker.meanLuma(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())), 1e-9)
        assertEquals(0.0, Deflicker.meanLuma(IntArray(0)), 0.0)
    }
}
