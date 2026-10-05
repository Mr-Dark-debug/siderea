package io.github.mrdarkdebug.siderea.core.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {
    @Test
    fun blackAndWhitePointsMapToTheEnds() {
        val levels = Levels(20f, 120f, 1f)
        assertEquals(0f, levels.map(10f), 0f)
        assertEquals(0f, levels.map(20f), 0f)
        assertEquals(255f, levels.map(120f), 0.01f)
        assertEquals(255f, levels.map(200f), 0f)
    }

    @Test
    fun gammaLiftsTheMidtonesAndStaysMonotonic() {
        val flat = Levels(0f, 255f, 1f)
        val lifted = Levels(0f, 255f, 2f)
        assertTrue(lifted.map(60f) > flat.map(60f))
        var last = -1f
        for (v in 0..255) {
            val m = lifted.map(v.toFloat())
            assertTrue(m >= last)
            last = m
        }
    }

    @Test
    fun automaticLevelsBrightenADarkSkyWithoutClippingStars() {
        val sky = SyntheticSky()
        val frame = sky.frame(noise = 3.0, background = 15.0)
        val levels = Levels.automatic(frame)
        val out = levels.apply(frame)

        fun mean(image: RgbImage) = image.pixels.indices.sumOf { image.luma(it).toDouble() } / image.pixels.size
        assertTrue("sky should get brighter: ${mean(frame)} -> ${mean(out)}", mean(out) > mean(frame) * 1.2)
        assertTrue(levels.black <= 20f)
        assertTrue(levels.white > levels.black)
    }

    @Test
    fun noneLeavesValuesAlone() {
        assertEquals(77f, Levels.NONE.map(77f), 0.01f)
    }
}
