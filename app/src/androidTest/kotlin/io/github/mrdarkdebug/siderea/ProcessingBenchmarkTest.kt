package io.github.mrdarkdebug.siderea

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import io.github.mrdarkdebug.siderea.core.processing.Alignment
import io.github.mrdarkdebug.siderea.core.processing.FileFrameSource
import io.github.mrdarkdebug.siderea.core.processing.RgbImage
import io.github.mrdarkdebug.siderea.core.processing.RgbImageIo
import io.github.mrdarkdebug.siderea.core.processing.StackAccumulator
import io.github.mrdarkdebug.siderea.core.processing.StarDetector
import io.github.mrdarkdebug.siderea.core.processing.TrailAccumulator
import io.github.mrdarkdebug.siderea.core.processing.Transform
import io.github.mrdarkdebug.siderea.export.AstroMemory
import io.github.mrdarkdebug.siderea.export.AstroMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.exp
import kotlin.system.measureTimeMillis

/**
 * Times the pure-Kotlin processing core on 12 MP frames and writes the numbers to logcat (tag `SideriaBench`).
 * The thresholds are deliberately loose: they catch an accidental order-of-magnitude regression, not a slow
 * emulator. The figures that matter come from running this on a real phone.
 */
class ProcessingBenchmarkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var work: File

    @Before
    fun setUp() {
        work = File(context.cacheDir, "bench").also { it.deleteRecursively() }
        work.mkdirs()
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun sky(
        width: Int,
        height: Int,
        dx: Double,
        seed: Long,
    ): RgbImage {
        val rng = Random(7)
        val noise = Random(seed)
        val luma = FloatArray(width * height) { 18f }
        repeat(STARS) {
            val x = 40 + rng.nextDouble() * (width - 80) + dx
            val y = 40 + rng.nextDouble() * (height - 80)
            val peak = 80 + rng.nextDouble() * 170
            for (py in (y.toInt() - 4)..(y.toInt() + 4)) {
                for (px in (x.toInt() - 4)..(x.toInt() + 4)) {
                    if (px in 0 until width && py in 0 until height) {
                        val d2 = (px - x) * (px - x) + (py - y) * (py - y)
                        luma[py * width + px] += (peak * exp(-d2 / 3.4)).toFloat()
                    }
                }
            }
        }
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val v = (luma[i] + noise.nextGaussian() * 6).toInt().coerceIn(0, 255)
            pixels[i] = RgbImage.pack(v, v, v)
        }
        return RgbImage(width, height, pixels)
    }

    @Test
    fun twelveMegapixelFramesAreProcessedInReasonableTime() {
        val sample = AstroMemory.sampleFor(WIDTH, HEIGHT, AstroMode.STACK)
        val w = WIDTH / sample
        val h = HEIGHT / sample
        val frames = List(4) { sky(w, h, it * 3.0, 10L + it) }
        val files =
            frames.mapIndexed {
                i,
                image,
                ->
                File(work, "IMG_%06d.jpg".format(i + 1)).also { RgbImageIo.writeJpeg(image, it) }
            }
        val timings = LinkedHashMap<String, Long>()

        val source = FileFrameSource(files, 1)
        var decoded: RgbImage? = null
        timings["decode JPEG"] = measureTimeMillis { decoded = source.load(0) }
        val image = checkNotNull(decoded) { "the JPEG did not decode" }
        assertEquals(w, image.width)

        var stars = emptyList<io.github.mrdarkdebug.siderea.core.processing.Star>()
        timings["detect stars"] = measureTimeMillis { stars = StarDetector.detect(image) }
        val other = StarDetector.detect(frames[1])
        timings["align (match stars)"] = measureTimeMillis { Alignment.estimate(stars, other) }

        val stack = StackAccumulator(w, h)
        timings["stack add (warped)"] = measureTimeMillis { stack.add(frames[1], Transform(1.0, 0.0001, -3.0, 0.5)) }
        timings["stack add (direct)"] = measureTimeMillis { stack.add(frames[2]) }
        val trails = TrailAccumulator(w, h)
        timings["trail add"] = measureTimeMillis { trails.add(frames[3]) }
        timings["stack to 16-bit TIFF"] =
            measureTimeMillis { File(work, "s.tif").outputStream().buffered().use { stack.writeTiff16(it) } }

        val line = timings.entries.joinToString { "${it.key} ${it.value} ms" }
        Log.i("SideriaBench", "${w}x$h (1/$sample of 12 MP), ${stars.size} stars: $line")
        // Loose guards: a real phone is far faster than these; they only catch something badly wrong.
        assertTrue("detect too slow: $timings", timings.getValue("detect stars") < SLOW_MS)
        assertTrue("warp too slow: $timings", timings.getValue("stack add (warped)") < SLOW_MS)
        assertTrue("tiff too slow: $timings", timings.getValue("stack to 16-bit TIFF") < SLOW_MS)
    }

    private companion object {
        const val WIDTH = 4000
        const val HEIGHT = 3000
        const val STARS = 150
        const val SLOW_MS = 60_000L
    }
}
