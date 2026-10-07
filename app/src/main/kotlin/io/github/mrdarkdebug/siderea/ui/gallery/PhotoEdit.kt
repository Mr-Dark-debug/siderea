package io.github.mrdarkdebug.siderea.ui.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.roundToInt

enum class CropAspect(
    val label: String,
    val ratio: Float?,
) {
    ORIGINAL("Original", null),
    SQUARE("Square", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    WIDE("16:9", 16f / 9f),
}

data class PhotoEdit(
    val turns: Int = 0,
    val mirror: Boolean = false,
    val crop: CropAspect = CropAspect.ORIGINAL,
    val zoom: Float = 1f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
)

/** One output allocation: compose orientation, crop and color into a single Canvas pass. */
object PhotoRenderer {
    // Quarter turns and the standard 8-bit color matrix use fixed mathematical coefficients.
    @Suppress("MagicNumber")
    fun render(
        source: Bitmap,
        edit: PhotoEdit,
    ): Bitmap {
        val turns = Math.floorMod(edit.turns, 4)
        val width = if (turns % 2 == 0) source.width else source.height
        val height = if (turns % 2 == 0) source.height else source.width
        val ratio = edit.crop.ratio ?: (width.toFloat() / height)
        val cropWidth = minOf(width.toFloat(), height * ratio) / edit.zoom.coerceIn(1f, 3f)
        val cropHeight = cropWidth / ratio
        val left = (width - cropWidth) * (edit.positionX.coerceIn(-1f, 1f) + 1f) / 2f
        val top = (height - cropHeight) * (edit.positionY.coerceIn(-1f, 1f) + 1f) / 2f
        val matrix =
            Matrix().apply {
                setRotate(turns * 90f)
                when (turns) {
                    1 -> postTranslate(source.height.toFloat(), 0f)
                    2 -> postTranslate(source.width.toFloat(), source.height.toFloat())
                    3 -> postTranslate(0f, source.width.toFloat())
                }
                if (edit.mirror) {
                    postScale(-1f, 1f)
                    postTranslate(width.toFloat(), 0f)
                }
                postTranslate(-left, -top)
            }
        val output =
            Bitmap.createBitmap(
                cropWidth.roundToInt().coerceAtLeast(1),
                cropHeight.roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        val contrast = edit.contrast.coerceIn(0.5f, 1.5f)
        val offset = 255f * edit.brightness.coerceIn(-0.5f, 0.5f) + 128f * (1f - contrast)
        val warmth = edit.warmth.coerceIn(-1f, 1f) * 30f
        val colors =
            ColorMatrix().apply {
                setSaturation(edit.saturation.coerceIn(0f, 2f))
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            contrast,
                            0f,
                            0f,
                            0f,
                            offset + warmth,
                            0f,
                            contrast,
                            0f,
                            0f,
                            offset,
                            0f,
                            0f,
                            contrast,
                            0f,
                            offset - warmth,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                        ),
                    ),
                )
            }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colors) }
        Canvas(output).drawBitmap(source, matrix, paint)
        return output
    }
}
