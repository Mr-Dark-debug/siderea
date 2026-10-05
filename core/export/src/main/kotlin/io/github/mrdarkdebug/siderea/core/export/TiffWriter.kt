package io.github.mrdarkdebug.siderea.core.export

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes uncompressed baseline RGB TIFF files, 8 or 16 bits per channel, streaming row by row so a 50 MP image
 * never has to sit in memory twice. The layout is fixed: header, one directory, its out-of-line values, then
 * the pixel strips, so every offset is known before the first pixel is written.
 */
object TiffWriter {
    private const val SAMPLES = 3
    private const val TARGET_STRIP_BYTES = 65_536
    private const val ENTRY_SIZE = 12
    private const val COUNT_FIELD = 2
    private const val NEXT_IFD_FIELD = 4
    private const val LONG_SIZE = 4
    private const val HEADER_SIZE = 8
    private const val TAG_COUNT = 10
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val BYTE_ORDER_MARK = 0x4949
    private const val MAGIC = 42
    private const val PHOTOMETRIC_RGB = 2
    private const val COMPRESSION_NONE = 1
    private const val PLANAR_CHUNKY = 1
    private const val BITS_8 = 8
    private const val BITS_16 = 16

    private const val TAG_WIDTH = 256
    private const val TAG_HEIGHT = 257
    private const val TAG_BITS = 258
    private const val TAG_COMPRESSION = 259
    private const val TAG_PHOTOMETRIC = 262
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_SAMPLES = 277
    private const val TAG_ROWS_PER_STRIP = 278
    private const val TAG_STRIP_COUNTS = 279
    private const val TAG_PLANAR = 284

    /**
     * Calls [row] once per image row, top to bottom, with a buffer to fill with `width * 3` samples: one byte
     * each for 8-bit, two little-endian bytes each for 16-bit.
     */
    fun writeRgb(
        out: OutputStream,
        width: Int,
        height: Int,
        bitsPerSample: Int,
        row: (y: Int, buffer: ByteArray) -> Unit,
    ) {
        require(bitsPerSample == BITS_8 || bitsPerSample == BITS_16) { "Only 8 and 16 bits per channel are supported" }
        require(width > 0 && height > 0) { "Empty image" }
        val rowBytes = width * SAMPLES * (bitsPerSample / BITS_8)
        val rowsPerStrip = (TARGET_STRIP_BYTES / rowBytes).coerceIn(1, height)
        val strips = (height + rowsPerStrip - 1) / rowsPerStrip

        val ifdSize = COUNT_FIELD + TAG_COUNT * ENTRY_SIZE + NEXT_IFD_FIELD
        val bitsOffset = HEADER_SIZE + ifdSize
        val offsetsOffset = bitsOffset + SAMPLES * 2
        val outOfLine = strips > 1
        val arrayBytes = if (outOfLine) strips * LONG_SIZE else 0
        val countsOffset = offsetsOffset + arrayBytes
        val dataOffset = countsOffset + arrayBytes

        val head = ByteBuffer.allocate(dataOffset).order(ByteOrder.LITTLE_ENDIAN)
        head.putShort(BYTE_ORDER_MARK.toShort()).putShort(MAGIC.toShort()).putInt(HEADER_SIZE)
        head.putShort(TAG_COUNT.toShort())
        entry(head, TAG_WIDTH, TYPE_LONG, 1, width)
        entry(head, TAG_HEIGHT, TYPE_LONG, 1, height)
        entry(head, TAG_BITS, TYPE_SHORT, SAMPLES, bitsOffset)
        entry(head, TAG_COMPRESSION, TYPE_SHORT, 1, COMPRESSION_NONE)
        entry(head, TAG_PHOTOMETRIC, TYPE_SHORT, 1, PHOTOMETRIC_RGB)
        entry(head, TAG_STRIP_OFFSETS, TYPE_LONG, strips, if (outOfLine) offsetsOffset else dataOffset)
        entry(head, TAG_SAMPLES, TYPE_SHORT, 1, SAMPLES)
        entry(head, TAG_ROWS_PER_STRIP, TYPE_LONG, 1, rowsPerStrip)
        entry(head, TAG_STRIP_COUNTS, TYPE_LONG, strips, if (outOfLine) countsOffset else rowsPerStrip * rowBytes)
        entry(head, TAG_PLANAR, TYPE_SHORT, 1, PLANAR_CHUNKY)
        head.putInt(0) // no further directory
        repeat(SAMPLES) { head.putShort(bitsPerSample.toShort()) }
        if (outOfLine) {
            for (s in 0 until strips) head.putInt(dataOffset + s * rowsPerStrip * rowBytes)
            for (s in 0 until strips) head.putInt(rowsInStrip(s, rowsPerStrip, height) * rowBytes)
        }
        out.write(head.array(), 0, head.position())

        val buffer = ByteArray(rowBytes)
        for (y in 0 until height) {
            row(y, buffer)
            out.write(buffer)
        }
        out.flush()
    }

    private fun rowsInStrip(
        strip: Int,
        rowsPerStrip: Int,
        height: Int,
    ): Int = minOf(rowsPerStrip, height - strip * rowsPerStrip)

    private fun entry(
        buffer: ByteBuffer,
        tag: Int,
        type: Int,
        count: Int,
        value: Int,
    ) {
        buffer.putShort(tag.toShort()).putShort(type.toShort()).putInt(count)
        // A single SHORT sits in the first two bytes of the value field; everything else is a full 4-byte value.
        if (type == TYPE_SHORT && count == 1) {
            buffer.putShort(value.toShort()).putShort(0)
        } else {
            buffer.putInt(value)
        }
    }
}
