package io.github.mrdarkdebug.siderea.core.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class VirtualBulbTest {
    private fun flat(
        value: Int,
        width: Int = 8,
        height: Int = 6,
    ) = RgbImage(width, height, IntArray(width * height) { RgbImage.pack(value, value, value) })

    private fun encode(linear: Double): Int = (linear.coerceIn(0.0, 1.0).pow(1 / 2.2) * 255 + 0.5).toInt()

    private fun lin(value: Int): Double = (value / 255.0).pow(2.2)

    @Test
    fun additiveSumsLightNotEncodedValues() {
        val source = ListSource(List(2) { flat(100) })
        val image = checkNotNull(VirtualBulbProcessor.combine(source, BulbMode.ADDITIVE, gain = 1f))
        // Two equal exposures doubled in linear light, not 100 + 100 = 200 in encoded values.
        assertEquals(encode(2 * lin(100)), image.red(0))
        assertTrue(image.red(0) in 135..143)
    }

    @Test
    fun additiveBrightnessGrowsWithExposureTime() {
        fun brightness(frames: Int): Int =
            checkNotNull(
                VirtualBulbProcessor.combine(ListSource(List(frames) { flat(30) }), BulbMode.ADDITIVE, gain = 1f),
            ).red(0)
        val one = brightness(1)
        val four = brightness(4)
        assertEquals(encode(4 * lin(30)), four)
        assertTrue(four > one)
    }

    @Test
    fun aLongExposureOfALitSceneClipsUnlessTheGainProtectsIt() {
        val frames = List(10) { flat(180) }
        val clipped = checkNotNull(VirtualBulbProcessor.combine(ListSource(frames), BulbMode.ADDITIVE, gain = 1f))
        assertEquals(255, clipped.red(0))
        val protectedImage = checkNotNull(VirtualBulbProcessor.combine(ListSource(frames), BulbMode.ADDITIVE))
        assertTrue("the automatic gain should keep highlights just under white", protectedImage.red(0) in 240..255)
    }

    @Test
    fun protectingGainNeverBrightensAnUnderExposedSum() {
        val acc = AdditiveAccumulator(8, 6)
        acc.add(flat(20))
        assertEquals(1f, acc.protectingGain(), 0f)
        assertEquals(1, acc.frames)
    }

    @Test
    fun lightenKeepsEveryMovingLight() {
        val width = 60
        val frames =
            List(6) { i ->
                val p = IntArray(width * 4) { RgbImage.pack(5, 5, 5) }
                p[width + 5 + i * 10] = RgbImage.pack(240, 240, 240)
                RgbImage(width, 4, p)
            }
        val image = checkNotNull(VirtualBulbProcessor.combine(ListSource(frames), BulbMode.LIGHTEN))
        for (i in 0 until 6) assertEquals(240, image.red(width + 5 + i * 10))
        assertEquals(5, image.red(0))
    }

    @Test
    fun averageRemovesSomethingThatWasOnlyThereBriefly() {
        val frames =
            List(5) { i ->
                val p = IntArray(10) { RgbImage.pack(50, 50, 50) }
                if (i == 2) p[3] = RgbImage.pack(250, 250, 250)
                RgbImage(10, 1, p)
            }
        val image = checkNotNull(VirtualBulbProcessor.combine(ListSource(frames), BulbMode.AVERAGE))
        assertEquals(50, image.red(0))
        assertEquals(90, image.red(3))
    }

    @Test
    fun unreadableAndMismatchedFramesAreSkipped() {
        val frames: List<RgbImage?> = listOf(flat(60), null, flat(60, width = 4, height = 4), flat(60))
        val progress = ArrayList<Int>()
        val image =
            checkNotNull(
                VirtualBulbProcessor.combine(ListSource(frames), BulbMode.ADDITIVE, gain = 1f, onProgress = { d, _ ->
                    progress +=
                        d
                }),
            )
        assertEquals(8, image.width)
        assertEquals(encode(2 * lin(60)), image.red(0))
        assertEquals(listOf(1, 2, 3, 4), progress)
    }

    @Test
    fun nothingReadableGivesNoImage() {
        BulbMode.entries.forEach { assertNull(VirtualBulbProcessor.combine(ListSource(listOf(null, null)), it)) }
    }

    @Test
    fun cancellationStopsTheWork() {
        var calls = 0
        try {
            VirtualBulbProcessor.combine(ListSource(List(10) { flat(10) }), BulbMode.ADDITIVE, checkCancelled = {
                if (++calls == 2) throw IllegalStateException("stop")
            })
            error("should have been cancelled")
        } catch (e: IllegalStateException) {
            assertEquals("stop", e.message)
        }
    }
}
