package io.github.mrdarkdebug.siderea.core.export

import android.graphics.Bitmap
import java.io.File

/** Saves a bitmap as an 8-bit uncompressed TIFF. */
object BitmapTiff {
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE = 0xFF

    /** [bitmap] must be readable on the CPU (not a hardware bitmap). */
    fun write(
        bitmap: Bitmap,
        output: File,
    ) {
        val width = bitmap.width
        val pixels = IntArray(width)
        output.parentFile?.mkdirs()
        try {
            output.outputStream().buffered().use { out ->
                TiffWriter.writeRgb(out, width, bitmap.height, BITS) { y, row ->
                    bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
                    for (x in 0 until width) {
                        val p = pixels[x]
                        row[x * CHANNELS] = ((p shr RED_SHIFT) and BYTE).toByte()
                        row[x * CHANNELS + 1] = ((p shr GREEN_SHIFT) and BYTE).toByte()
                        row[x * CHANNELS + 2] = (p and BYTE).toByte()
                    }
                }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            output.delete()
            throw e
        }
    }

    private const val BITS = 8
    private const val CHANNELS = 3
}
