package io.github.mrdarkdebug.siderea.core.processing

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import io.github.mrdarkdebug.siderea.core.export.TiffWriter
import java.io.File

/**
 * Session JPEGs as a [FrameSource]. Frames are decoded with their EXIF rotation applied and, when [sample] is
 * above 1, reduced by that factor so a big stack fits in memory.
 */
class FileFrameSource(
    private val files: List<File>,
    private val sample: Int = 1,
) : FrameSource {
    override val count: Int get() = files.size

    override fun load(index: Int): RgbImage? =
        runCatching {
            val bitmap =
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(files[index])) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    if (sample > 1) decoder.setTargetSampleSize(sample)
                }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val image = RgbImage(bitmap.width, bitmap.height, pixels)
            bitmap.recycle()
            for (i in pixels.indices) pixels[i] = pixels[i] or RgbImage.OPAQUE
            image
        }.getOrNull()
}

/** Writing finished images to disk. */
object RgbImageIo {
    private const val JPEG_QUALITY = 95
    private const val CHANNELS = 3
    private const val BITS = 8

    fun writeJpeg(
        image: RgbImage,
        output: File,
    ) {
        val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        try {
            output.parentFile?.mkdirs()
            output.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            output.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
    }

    fun writeTiff8(
        image: RgbImage,
        output: File,
    ) {
        output.parentFile?.mkdirs()
        try {
            output.outputStream().buffered().use { out ->
                TiffWriter.writeRgb(out, image.width, image.height, BITS) { y, row ->
                    for (x in 0 until image.width) {
                        val i = y * image.width + x
                        row[x * CHANNELS] = image.red(i).toByte()
                        row[x * CHANNELS + 1] = image.green(i).toByte()
                        row[x * CHANNELS + 2] = image.blue(i).toByte()
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
}
