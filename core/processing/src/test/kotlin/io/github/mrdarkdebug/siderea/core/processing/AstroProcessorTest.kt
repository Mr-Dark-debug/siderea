package io.github.mrdarkdebug.siderea.core.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.sqrt

class AstroProcessorTest {
    private val sky = SyntheticSky()

    /** Frames of the same sky, each wandering a little, with real-looking noise. */
    private fun wanderingFrames(
        n: Int,
        noise: Double = 12.0,
    ): List<RgbImage> =
        List(n) { i ->
            sky.frame(
                dx = (i % 5) * 3.1 - 6.0,
                dy = (i % 3) * -2.7 + 2.0,
                degrees = i * 0.05,
                noise = noise,
                seed = 100L + i,
            )
        }

    private fun backgroundNoise(image: RgbImage): Double {
        // A star-free patch of the synthetic sky (checked: stars are placed 30 px in from every edge).
        val values = ArrayList<Double>()
        for (y in 2..20) for (x in 2..20) values += image.luma(y * image.width + x).toDouble()
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    @Test
    fun stackingReducesNoiseByRoughlyTheSquareRootOfTheFrameCount() {
        val frames = wanderingFrames(16)
        val single = backgroundNoise(frames[0])
        val result = checkNotNull(AstroProcessor.stack(ListSource(frames)))
        assertEquals(16, result.used)
        val stacked = backgroundNoise(result.accumulator.toRgbImage())
        // Edge pixels see fewer frames, so allow generous slack: 4x ideal, anything better than 2.5x passes.
        assertTrue("noise $single -> $stacked", stacked < single / 2.5)
    }

    @Test
    fun alignedStackKeepsStarsSharpWhereAPlainAverageSmearsThem() {
        val frames = wanderingFrames(10, noise = 3.0)
        val aligned = checkNotNull(AstroProcessor.stack(ListSource(frames))).accumulator.toRgbImage()

        val plain = StackAccumulator(sky.width, sky.height)
        frames.forEach { plain.add(it) }
        val smeared = plain.toRgbImage()

        fun peak(image: RgbImage): Float {
            val brightest = sky.stars.maxBy { it.peak }
            val x = brightest.x.toInt()
            val y = brightest.y.toInt()
            var best = 0f
            for (dy in -8..8) for (dx in -8..8) best = maxOf(best, image.luma((y + dy) * image.width + x + dx))
            return best
        }
        assertTrue("aligned ${peak(aligned)} vs plain ${peak(smeared)}", peak(aligned) > peak(smeared) * 1.2f)
    }

    @Test
    fun alignedStarsSitWhereTheReferenceHasThem() {
        val frames = wanderingFrames(8, noise = 3.0)
        val result = checkNotNull(AstroProcessor.stack(ListSource(frames)))
        val stars = StarDetector.detect(result.accumulator.toRgbImage(), maxStars = 80)
        val reference = StarDetector.detect(frames[result.referenceIndex], maxStars = 80)
        var close = 0
        for (r in reference.take(20)) {
            val s = stars.minByOrNull { hypot(it.x - r.x, it.y - r.y) } ?: continue
            if (hypot(s.x - r.x, s.y - r.y) < 0.5) close++
        }
        assertTrue("only $close of 20 stars stayed put", close >= 17)
        assertTrue(result.maxShiftPixels > 3.0)
    }

    @Test
    fun framesThatCannotBeUsedAreReportedNotStacked() {
        val good = wanderingFrames(5, noise = 4.0)
        val strangers = SyntheticSky(seed = 5).frame(noise = 4.0, seed = 9)
        val blank = SyntheticSky(starCount = 0).frame(noise = 4.0, seed = 8)
        val small = SyntheticSky(width = 100, height = 80).frame()
        val frames: List<RgbImage?> = listOf(good[0], strangers, null, good[1], blank, small, good[2])
        val result = checkNotNull(AstroProcessor.stack(ListSource(frames)))
        assertEquals(3, result.used)
        val reasons = result.rejected.associate { it.index to it.reason }
        assertEquals(RejectReason.NO_MATCH, reasons[1])
        assertEquals(RejectReason.UNREADABLE, reasons[2])
        assertEquals(RejectReason.TOO_FEW_STARS, reasons[4])
        assertEquals(RejectReason.WRONG_SIZE, reasons[5])
    }

    @Test
    fun noStarsAnywhereMeansNoStack() {
        val blank = SyntheticSky(starCount = 0)
        assertNull(AstroProcessor.stack(ListSource(List(4) { blank.frame(noise = 3.0, seed = it.toLong()) })))
    }

    @Test
    fun darkSubtractionRemovesHotPixelsAndKeepsStars() {
        val hot = listOf(Triple(40, 40, 220), Triple(300, 200, 180))
        val darks =
            List(
                4,
            ) { SyntheticSky(starCount = 0).frame(noise = 2.0, background = 4.0, seed = 50L + it, hotPixels = hot) }
        val dark = checkNotNull(MasterDark.average(ListSource(darks)))
        assertEquals(4, dark.frames)

        val light = sky.frame(noise = 2.0, seed = 7, hotPixels = hot)
        val before = light.luma(40 * light.width + 40)
        val cleaned = dark.subtractFrom(RgbImage(light.width, light.height, light.pixels.copyOf()))
        val after = cleaned.luma(40 * light.width + 40)
        assertTrue("hot pixel $before -> $after", before > 150 && after < 30)

        val star = sky.stars.maxBy { it.peak }
        val i = star.y.toInt() * light.width + star.x.toInt()
        assertTrue("star kept: ${light.luma(i)} -> ${cleaned.luma(i)}", cleaned.luma(i) > light.luma(i) * 0.9f)
    }

    @Test
    fun masterDarkNeedsAtLeastOneReadableFrame() {
        assertNull(MasterDark.average(ListSource(listOf(null, null))))
    }

    @Test
    fun trailsKeepEveryPositionAMovingStarVisited() {
        val width = 200
        val height = 60
        val frames =
            List(10) { i ->
                val pixels = IntArray(width * height) { RgbImage.pack(10, 10, 10) }
                val x = 20 + i * 15
                for (dx in -1..1) pixels[30 * width + x + dx] = RgbImage.pack(250, 250, 250)
                RgbImage(width, height, pixels)
            }
        val trail = checkNotNull(AstroProcessor.trails(ListSource(frames)))
        for (i in 0 until 10) assertEquals(250, trail.red(30 * width + 20 + i * 15))
        assertEquals(10, trail.red(10 * width + 5))
    }

    @Test
    fun cometTailsFadeTowardsTheOldEnd() {
        val width = 120
        val height = 20
        val frames =
            List(10) { i ->
                val pixels = IntArray(width * height) { RgbImage.pack(0, 0, 0) }
                pixels[10 * width + 10 + i * 10] = RgbImage.pack(200, 200, 200)
                RgbImage(width, height, pixels)
            }
        val comet = checkNotNull(AstroProcessor.trails(ListSource(frames), cometFade = 0.7f))
        val oldest = comet.red(10 * width + 10)
        val newest = comet.red(10 * width + 100)
        assertEquals(200, newest)
        assertTrue("oldest $oldest should be much dimmer than newest $newest", oldest < 30)
    }

    @Test
    fun trailsSkipUnreadableFramesAndReportProgress() {
        val frame = sky.frame(noise = 2.0)
        val progress = ArrayList<Int>()
        val image = AstroProcessor.trails(ListSource(listOf(null, frame, null)), onProgress = { d, _ -> progress += d })
        assertNotNull(image)
        assertEquals(listOf(1, 2, 3), progress)
        assertNull(AstroProcessor.trails(ListSource(listOf(null))))
    }

    @Test
    fun cancellationStopsTheWorkAtOnce() {
        var calls = 0
        try {
            AstroProcessor.trails(ListSource(List(20) { sky.frame() }), checkCancelled = {
                calls++
                if (calls == 3) throw IllegalStateException("stop")
            })
            error("should have thrown")
        } catch (e: IllegalStateException) {
            assertEquals("stop", e.message)
        }
        assertEquals(3, calls)
    }

    @Test
    fun sixteenBitTiffKeepsTheFractionalMean() {
        val acc = StackAccumulator(4, 2)
        val a = RgbImage(4, 2, IntArray(8) { RgbImage.pack(100, 50, 0) })
        val b = RgbImage(4, 2, IntArray(8) { RgbImage.pack(101, 50, 0) })
        acc.add(a)
        acc.add(b)
        assertEquals(100.5f, acc.mean(0, 0), 1e-4f)
        val out = ByteArrayOutputStream()
        acc.writeTiff16(out)
        val bytes = out.toByteArray()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Pixel data is the last 4*2*3*2 bytes of the file.
        val first = buf.getShort(bytes.size - 48).toInt() and 0xFFFF
        assertEquals((100.5f / 255f * 65535f + 0.5f).toInt(), first)
        assertTrue("16-bit holds a value an 8-bit file cannot", first % 257 != 0)
    }
}
