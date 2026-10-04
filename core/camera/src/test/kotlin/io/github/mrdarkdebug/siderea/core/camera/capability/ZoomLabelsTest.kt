package io.github.mrdarkdebug.siderea.core.camera.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomLabelsTest {
    @Test
    fun `equivalent focal length follows sensor diagonal`() {
        // A 6.81 mm lens over a 9.8 x 7.35 mm sensor frames like a 24 mm lens on full frame.
        val equivalent = ZoomLabels.equivalentFocalLengthMm(6.81f, 9.8f, 7.35f)!!
        assertEquals(24.05f, equivalent, 0.1f)
    }

    @Test
    fun `a full-frame sensor leaves the focal length unchanged`() {
        assertEquals(50f, ZoomLabels.equivalentFocalLengthMm(50f, 36f, 24f)!!, 0.01f)
    }

    @Test
    fun `missing or zero sensor size yields null`() {
        assertNull(ZoomLabels.equivalentFocalLengthMm(6f, null, 5f))
        assertNull(ZoomLabels.equivalentFocalLengthMm(6f, 5f, null))
        assertNull(ZoomLabels.equivalentFocalLengthMm(6f, 0f, 5f))
    }

    @Test
    fun `ultra-wide and main and tele get familiar labels`() {
        val main = 24f
        assertEquals("0.6x", ZoomLabels.format(15f / main)) // 0.625 rounds to 0.6
        assertEquals("1x", ZoomLabels.format(1f))
        assertEquals("5x", ZoomLabels.format(113f / main)) // 4.7
        assertEquals("10x", ZoomLabels.format(230f / main)) // 9.6
    }

    @Test
    fun `formatting rounds to the right granularity`() {
        assertEquals("0.5x", ZoomLabels.format(0.5f))
        assertEquals("1x", ZoomLabels.format(0.98f))
        assertEquals("2x", ZoomLabels.format(2.2f))
        assertEquals("2.5x", ZoomLabels.format(2.3f))
        assertEquals("3x", ZoomLabels.format(2.9f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive ratios are rejected`() {
        ZoomLabels.format(0f)
    }

    @Test
    fun `main lens is the one closest to a typical main focal length`() {
        val main = ZoomLabels.pickMain(listOf("2" to 15f, "0" to 24f, "3" to 113f))
        assertEquals("0", main)
    }

    @Test
    fun `main lens selection is symmetric in log space`() {
        // 13 mm and 52 mm are both a factor of 2 from 26 mm; the earlier candidate wins the tie.
        assertEquals("a", ZoomLabels.pickMain(listOf("a" to 13f, "b" to 52f)))
    }

    @Test
    fun `main lens of a single camera is that camera and of none is null`() {
        assertEquals("7", ZoomLabels.pickMain(listOf("7" to 120f)))
        assertNull(ZoomLabels.pickMain(emptyList()))
    }
}
