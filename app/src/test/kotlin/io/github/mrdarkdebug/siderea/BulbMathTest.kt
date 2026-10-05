package io.github.mrdarkdebug.siderea

import io.github.mrdarkdebug.siderea.core.capture.timelapse.OverheadEstimate
import io.github.mrdarkdebug.siderea.ui.camera.astroIntervalMs
import io.github.mrdarkdebug.siderea.ui.camera.bulbFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulbMathTest {
    private val overhead = OverheadEstimate(overheadMs = 1_200, measured = true, samples = 3)

    @Test
    fun framesCoverTheTotalTimeAndRoundUp() {
        assertEquals(8, bulbFrames(120, 15_000_000_000L))
        assertEquals(9, bulbFrames(121, 15_000_000_000L))
        assertEquals(120, bulbFrames(60, 500_000_000L))
    }

    @Test
    fun atLeastTwoFramesSoThereIsSomethingToCombine() {
        assertEquals(2, bulbFrames(1, 16_000_000_000L))
        assertEquals(2, bulbFrames(10, 10_000_000_000L).coerceAtLeast(2))
    }

    @Test
    fun aTinyExposureStillGivesAFiniteCount() {
        assertTrue(bulbFrames(30, 0L) in 2..30_000)
    }

    @Test
    fun theIntervalIsNeverShorterThanTheTimeTheCameraNeedsToSave() {
        val exposure = 15_000_000_000L
        val interval = astroIntervalMs(exposure, gapMs = 0L, overhead = overhead)
        assertTrue("$interval should be at least exposure + overhead", interval >= 15_000 + 1_200)
        // A longer gap than the overhead wins.
        assertEquals(15_000 + 5_000L, astroIntervalMs(exposure, gapMs = 5_000L, overhead = overhead))
    }
}
