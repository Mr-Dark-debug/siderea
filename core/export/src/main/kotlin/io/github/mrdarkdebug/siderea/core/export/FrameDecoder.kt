package io.github.mrdarkdebug.siderea.core.export

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File

/** Decodes session frames with the camera's EXIF rotation already applied. */
object FrameDecoder {
    private const val PROBE_SAMPLE = 64

    /** The upright size of a frame, found without decoding its pixels. */
    fun size(file: File): PixelSize {
        var found: PixelSize? = null
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                found = PixelSize(info.size.width, info.size.height)
                decoder.setTargetSampleSize(PROBE_SAMPLE)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        return found ?: error("Cannot read ${file.name}")
    }

    /**
     * Decodes [file] at the largest power-of-two reduction that keeps the long side at or above
     * [minLongSide] (or at full size when null). [software] gives a CPU bitmap whose pixels can be read.
     */
    fun decode(
        file: File,
        minLongSide: Int?,
        software: Boolean,
    ): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            if (minLongSide != null) {
                val longSide = maxOf(info.size.width, info.size.height)
                var sample = 1
                while (longSide / (sample * 2) >= minLongSide) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
            if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
}
