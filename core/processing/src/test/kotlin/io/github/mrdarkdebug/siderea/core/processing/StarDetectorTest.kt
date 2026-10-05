package io.github.mrdarkdebug.siderea.core.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StarDetectorTest {
    private val sky = SyntheticSky()

    @Test
    fun findsMostStarsWithSubPixelAccuracy() {
        val found = StarDetector.detect(sky.frame(noise = 4.0), maxStars = 80)
        var matched = 0
        for (s in sky.stars) {
            val near = found.minByOrNull { hypot(it.x - s.x, it.y - s.y) } ?: continue
            if (hypot(near.x - s.x, near.y - s.y) < 0.4) matched++
        }
        assertTrue("matched only $matched of ${sky.stars.size}", matched >= sky.stars.size * 0.9)
    }

    @Test
    fun aNoiseOnlyImageHasNoStars() {
        val empty = SyntheticSky(starCount = 0).frame(noise = 6.0)
        assertEquals(0, StarDetector.detect(empty).size)
    }

    @Test
    fun brightestStarsComeFirstAndTheLimitHolds() {
        val found = StarDetector.detect(sky.frame(noise = 3.0), maxStars = 10)
        assertEquals(10, found.size)
        assertTrue(found.zipWithNext().all { (a, b) -> a.flux >= b.flux })
    }

    @Test
    fun hotPixelsAreNotStarsOnceNeighboursAreChecked() {
        // A single hot pixel has a sharp, one-pixel peak: it is detected as a candidate but with tiny flux.
        val frame = SyntheticSky(starCount = 0).frame(hotPixels = listOf(Triple(100, 100, 255)))
        val found = StarDetector.detect(frame)
        assertTrue(found.all { it.flux < 400 })
    }
}
