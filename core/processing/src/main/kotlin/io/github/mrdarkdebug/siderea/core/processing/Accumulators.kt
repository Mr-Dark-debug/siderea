@file:Suppress("MagicNumber") // Pixel packing and 8 -> 16 bit scaling.

package io.github.mrdarkdebug.siderea.core.processing

import io.github.mrdarkdebug.siderea.core.export.TiffWriter
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.stream.IntStream

/** Builds one image out of many frames, one at a time, so only a single frame is ever held in memory. */
interface FrameAccumulator {
    /** Adds [frame]. A non-null [transform] first moves it onto the reference frame. */
    fun add(
        frame: RgbImage,
        transform: Transform? = null,
    )

    /** The combined image as 8-bit RGB. */
    fun toRgbImage(): RgbImage
}

/**
 * Star trails: every pixel keeps the brightest value any frame gave it, so a star leaves a streak as it moves.
 * With a [fade] below 1 the older light dims each frame, which turns the streaks into comet tails.
 */
class TrailAccumulator(
    val width: Int,
    val height: Int,
    private val fade: Float = 1f,
) : FrameAccumulator {
    private val pixels = IntArray(width * height)

    init {
        require(fade in 0f..1f) { "fade must be between 0 and 1" }
        pixels.fill(RgbImage.OPAQUE)
    }

    override fun add(
        frame: RgbImage,
        transform: Transform?,
    ) {
        require(frame.width == width && frame.height == height) { "Frame size differs from the first frame" }
        val dim = (fade * 256).toInt()
        for (i in pixels.indices) {
            var old = pixels[i]
            if (fade < 1f) old = scale(old, dim)
            pixels[i] = lighten(old, frame.pixels[i])
        }
    }

    override fun toRgbImage(): RgbImage = RgbImage(width, height, pixels.copyOf())

    private fun scale(
        p: Int,
        factor: Int,
    ): Int {
        val r = ((p shr 16) and 0xFF) * factor shr 8
        val g = ((p shr 8) and 0xFF) * factor shr 8
        val b = (p and 0xFF) * factor shr 8
        return RgbImage.pack(r, g, b)
    }

    private fun lighten(
        a: Int,
        b: Int,
    ): Int {
        val r = maxOf((a shr 16) and 0xFF, (b shr 16) and 0xFF)
        val g = maxOf((a shr 8) and 0xFF, (b shr 8) and 0xFF)
        val bl = maxOf(a and 0xFF, b and 0xFF)
        return RgbImage.pack(r, g, bl)
    }
}

/**
 * Aligned average stacking. Each frame is warped onto the reference with bilinear sampling and added; the result
 * is the per-pixel mean, held in float so averaging many 8-bit frames gains real extra precision that the 16-bit
 * output keeps. Pixels that no frame covered (the edge a shifted frame leaves) stay black.
 */
class StackAccumulator(
    val width: Int,
    val height: Int,
) : FrameAccumulator {
    private val sum = FloatArray(width * height * 3)
    private val count = ShortArray(width * height)

    /** How many frames contributed to the pixel at the centre: a quick sanity figure for reports. */
    val centreCount: Int get() = count[(height / 2) * width + width / 2].toInt()

    override fun add(
        frame: RgbImage,
        transform: Transform?,
    ) {
        require(frame.width == width && frame.height == height) { "Frame size differs from the first frame" }
        if (transform == null || transform.isIdentity) {
            addDirect(frame)
        } else {
            addWarped(frame, transform)
        }
    }

    private fun addDirect(frame: RgbImage) {
        for (i in 0 until width * height) {
            val p = frame.pixels[i]
            sum[i * 3] += ((p shr 16) and 0xFF).toFloat()
            sum[i * 3 + 1] += ((p shr 8) and 0xFF).toFloat()
            sum[i * 3 + 2] += (p and 0xFF).toFloat()
            count[i]++
        }
    }

    private fun addWarped(
        frame: RgbImage,
        t: Transform,
    ) {
        // Invert the similarity: source = (dest - t) / z, with z = a + ib.
        val norm = t.a * t.a + t.b * t.b
        val ia = t.a / norm
        val ib = -t.b / norm
        // Rows write to different parts of the accumulator, so they can be warped on all cores at once.
        IntStream.range(0, height).parallel().forEach { y -> warpRow(frame, y, ia, ib, t) }
    }

    /** One destination row: the source position steps by a constant amount per pixel, so no per-pixel multiplies. */
    private fun warpRow(
        frame: RgbImage,
        y: Int,
        ia: Double,
        ib: Double,
        t: Transform,
    ) {
        val dy = y - t.ty
        var sx = ia * (0 - t.tx) - ib * dy
        var sy = ib * (0 - t.tx) + ia * dy
        val maxX = (width - 1).toDouble()
        val maxY = (height - 1).toDouble()
        val pixels = frame.pixels
        var target = y * width
        for (x in 0 until width) {
            if (sx >= 0 && sy >= 0 && sx <= maxX && sy <= maxY) {
                val x0 = minOf(sx.toInt(), width - 2)
                val y0 = minOf(sy.toInt(), height - 2)
                val fx = (sx - x0).toFloat()
                val fy = (sy - y0).toFloat()
                val i00 = y0 * width + x0
                val w00 = (1 - fx) * (1 - fy)
                val w10 = fx * (1 - fy)
                val w01 = (1 - fx) * fy
                val w11 = fx * fy
                val p00 = pixels[i00]
                val p10 = pixels[i00 + 1]
                val p01 = pixels[i00 + width]
                val p11 = pixels[i00 + width + 1]
                val o = target * 3
                sum[o] += ((p00 shr 16) and 0xFF) * w00 + ((p10 shr 16) and 0xFF) * w10 +
                    ((p01 shr 16) and 0xFF) * w01 + ((p11 shr 16) and 0xFF) * w11
                sum[o + 1] += ((p00 shr 8) and 0xFF) * w00 + ((p10 shr 8) and 0xFF) * w10 +
                    ((p01 shr 8) and 0xFF) * w01 + ((p11 shr 8) and 0xFF) * w11
                sum[o + 2] += (p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 + (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11
                count[target]++
            }
            sx += ia
            sy += ib
            target++
        }
    }

    /** The mean value (0..255, fractional) of one channel at one pixel. */
    fun mean(
        pixel: Int,
        channel: Int,
    ): Float {
        val n = count[pixel].toInt()
        return if (n == 0) 0f else sum[pixel * 3 + channel] / n
    }

    override fun toRgbImage(): RgbImage {
        val out = IntArray(width * height)
        for (i in out.indices) {
            out[i] =
                RgbImage.pack(
                    (mean(i, 0) + 0.5f).toInt().coerceIn(0, 255),
                    (mean(i, 1) + 0.5f).toInt().coerceIn(0, 255),
                    (mean(i, 2) + 0.5f).toInt().coerceIn(0, 255),
                )
        }
        return RgbImage(width, height, out)
    }

    /**
     * Writes the stack as a 16-bit TIFF. [stretch] optionally applies a levels curve (black point, white point,
     * gamma) to the 0..255 means first; null keeps them as they are.
     */
    fun writeTiff16(
        out: OutputStream,
        stretch: Levels? = null,
    ) {
        val row = ByteBuffer.allocate(width * 6).order(ByteOrder.LITTLE_ENDIAN)
        TiffWriter.writeRgb(out, width, height, 16) { y, buffer ->
            row.clear()
            for (x in 0 until width) {
                val i = y * width + x
                for (c in 0..2) {
                    val v = mean(i, c)
                    val mapped = stretch?.map(v) ?: v
                    row.putShort((mapped / 255f * 65535f + 0.5f).toInt().coerceIn(0, 65535).toShort())
                }
            }
            System.arraycopy(row.array(), 0, buffer, 0, buffer.size)
        }
    }
}
