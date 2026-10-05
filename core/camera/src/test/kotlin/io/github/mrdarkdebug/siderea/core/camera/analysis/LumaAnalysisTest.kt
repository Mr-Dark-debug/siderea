package io.github.mrdarkdebug.siderea.core.camera.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaAnalysisTest {
    private fun gray(value: Int) = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    @Test
    fun `uniform mid grey has one histogram bin and the right mean`() {
        val a = LumaAnalysis.analyze(IntArray(100) { gray(128) }, 10, 10)
        assertEquals(100, a.histogram[128])
        assertEquals(128f, a.meanLuma, 1f)
        assertEquals(0f, a.clippedHigh, 0f)
        assertEquals(0f, a.clippedLow, 0f)
        assertNull(a.peaking)
        assertNull(a.zebra)
    }

    @Test
    fun `clipped highlights and crushed shadows are measured`() {
        val pixels =
            IntArray(100) {
                if (it < 20) {
                    gray(255)
                } else if (it < 30) {
                    gray(0)
                } else {
                    gray(100)
                }
            }
        val a = LumaAnalysis.analyze(pixels, 10, 10)
        assertEquals(0.2f, a.clippedHigh, 0.001f)
        assertEquals(0.1f, a.clippedLow, 0.001f)
    }

    @Test
    fun `luma weights green most`() {
        val red = LumaAnalysis.luma(intArrayOf(0xFFFF0000.toInt()))[0]
        val green = LumaAnalysis.luma(intArrayOf(0xFF00FF00.toInt()))[0]
        val blue = LumaAnalysis.luma(intArrayOf(0xFF0000FF.toInt()))[0]
        assertTrue(green > red && red > blue)
        assertEquals(255, LumaAnalysis.luma(intArrayOf(gray(255)))[0])
    }

    @Test
    fun `a hard edge is peaked and a smooth ramp is not`() {
        val w = 40
        val h = 20
        val threshold = LumaAnalysis.DEFAULT_PEAKING_THRESHOLD
        val edge = IntArray(w * h) { if (it % w < w / 2) gray(0) else gray(255) }
        val ramp = IntArray(w * h) { gray((it % w) * 255 / (w - 1)) }
        val edgeMask = LumaAnalysis.analyze(edge, w, h, peakingThreshold = threshold).peaking!!
        val rampMask = LumaAnalysis.analyze(ramp, w, h, peakingThreshold = threshold).peaking!!
        assertTrue(edgeMask[5 * w + w / 2 - 1] || edgeMask[5 * w + w / 2])
        assertFalse("flat area must not peak", edgeMask[5 * w + 3])
        assertEquals("a gentle ramp is blur, not an edge", 0, rampMask.count { it })
    }

    @Test
    fun `border pixels never peak and nothing crashes on tiny frames`() {
        val mask = LumaAnalysis.peaking(IntArray(4), 2, 2, 10)
        assertEquals(0, mask.count { it })
    }

    @Test
    fun `zebra marks pixels at or above the threshold`() {
        val pixels = intArrayOf(gray(100), gray(234), gray(235), gray(255))
        val mask = LumaAnalysis.analyze(pixels, 4, 1, zebraThreshold = LumaAnalysis.ZEBRA_THRESHOLD).zebra!!
        assertEquals(listOf(false, false, true, true), mask.toList())
    }

    @Test
    fun `peaking and zebra are produced only when asked`() {
        val a = LumaAnalysis.analyze(IntArray(16) { gray(10) }, 4, 4, peakingThreshold = 30, zebraThreshold = 200)
        assertNotNull(a.peaking)
        assertNotNull(a.zebra)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a mismatched frame size is rejected`() {
        LumaAnalysis.analyze(IntArray(10), 4, 4)
    }
}
