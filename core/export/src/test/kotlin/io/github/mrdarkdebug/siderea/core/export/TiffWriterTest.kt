package io.github.mrdarkdebug.siderea.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes TIFFs and reads them back with a tiny independent parser, so the layout is checked, not assumed. */
class TiffWriterTest {
    private class Parsed(
        val width: Int,
        val height: Int,
        val bits: List<Int>,
        val photometric: Int,
        val compression: Int,
        val pixels: ByteArray,
    )

    private fun parse(bytes: ByteArray): Parsed {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x4949, buf.getShort(0).toInt() and 0xFFFF)
        assertEquals(42, buf.getShort(2).toInt())
        val ifd = buf.getInt(4)
        val count = buf.getShort(ifd).toInt()
        val values = HashMap<Int, IntArray>()
        for (i in 0 until count) {
            val at = ifd + 2 + i * 12
            val tag = buf.getShort(at).toInt() and 0xFFFF
            val type = buf.getShort(at + 2).toInt()
            val n = buf.getInt(at + 4)
            val size = if (type == 3) 2 else 4
            val base = if (n * size <= 4) at + 8 else buf.getInt(at + 8)
            values[tag] =
                IntArray(n) { k ->
                    if (type == 3) buf.getShort(base + k * 2).toInt() and 0xFFFF else buf.getInt(base + k * 4)
                }
        }
        val offsets = values.getValue(273)
        val counts = values.getValue(279)
        val pixels = ByteArray(counts.sum())
        var at = 0
        for (s in offsets.indices) {
            System.arraycopy(bytes, offsets[s], pixels, at, counts[s])
            at += counts[s]
        }
        assertEquals(1, values.getValue(284)[0])
        assertEquals(3, values.getValue(277)[0])
        return Parsed(
            values.getValue(256)[0],
            values.getValue(257)[0],
            values.getValue(258).toList(),
            values.getValue(262)[0],
            values.getValue(259)[0],
            pixels,
        )
    }

    private fun write(
        width: Int,
        height: Int,
        bits: Int,
        fill: (Int, ByteArray) -> Unit,
    ): ByteArray = ByteArrayOutputStream().also { TiffWriter.writeRgb(it, width, height, bits, fill) }.toByteArray()

    @Test
    fun eightBitImageRoundTrips() {
        val bytes =
            write(3, 2, 8) { y, row ->
                for (i in row.indices) row[i] = (y * 100 + i).toByte()
            }
        val tiff = parse(bytes)
        assertEquals(3, tiff.width)
        assertEquals(2, tiff.height)
        assertEquals(listOf(8, 8, 8), tiff.bits)
        assertEquals(2, tiff.photometric)
        assertEquals(1, tiff.compression)
        val expected = ByteArray(18) { i -> ((i / 9) * 100 + i % 9).toByte() }
        assertEquals(expected.toList(), tiff.pixels.toList())
    }

    @Test
    fun sixteenBitImageKeepsEverySample() {
        val bytes =
            write(2, 2, 16) { y, row ->
                val b = ByteBuffer.wrap(row).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until 6) b.putShort(i * 2, (y * 10_000 + i * 1_111).toShort())
            }
        val tiff = parse(bytes)
        assertEquals(listOf(16, 16, 16), tiff.bits)
        val samples = ByteBuffer.wrap(tiff.pixels).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, samples.getShort(0).toInt() and 0xFFFF)
        assertEquals(5 * 1_111, samples.getShort(10).toInt() and 0xFFFF)
        assertEquals(10_000 + 2 * 1_111, samples.getShort(2 * 6 + 4).toInt() and 0xFFFF)
    }

    @Test
    fun aTallImageUsesSeveralStripsAndStillReadsBackInOrder() {
        // 200 px wide x 8-bit RGB = 600 bytes per row, so 64 KiB strips hold 109 rows: 500 rows is 5 strips.
        val bytes = write(200, 500, 8) { y, row -> row.fill((y % 251).toByte()) }
        val tiff = parse(bytes)
        assertEquals(200 * 500 * 3, tiff.pixels.size)
        assertEquals(0, tiff.pixels[0].toInt())
        assertEquals((499 % 251).toByte(), tiff.pixels[499 * 600])
        assertEquals((250 % 251).toByte(), tiff.pixels[250 * 600 + 599])
    }

    @Test
    fun rejectsUnsupportedDepthAndEmptyImages() {
        assertThrows(IllegalArgumentException::class.java) { write(2, 2, 12) { _, _ -> } }
        assertThrows(IllegalArgumentException::class.java) { write(0, 2, 8) { _, _ -> } }
    }
}
