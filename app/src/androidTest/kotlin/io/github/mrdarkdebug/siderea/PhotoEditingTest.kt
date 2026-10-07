package io.github.mrdarkdebug.siderea

import android.graphics.Bitmap
import android.graphics.Color
import io.github.mrdarkdebug.siderea.ui.gallery.CropAspect
import io.github.mrdarkdebug.siderea.ui.gallery.PhotoEdit
import io.github.mrdarkdebug.siderea.ui.gallery.PhotoRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditingTest {
    private fun source(): Bitmap =
        Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(60, 90, 120))
            setPixel(0, 0, Color.RED)
            setPixel(3, 1, Color.BLUE)
        }

    @Test fun rotationAndMirrorMapPixelsWithoutBlankEdges() {
        val source = source()
        val rotated = PhotoRenderer.render(source, PhotoEdit(turns = 1))
        assertEquals(2, rotated.width)
        assertEquals(4, rotated.height)
        assertEquals(Color.RED, rotated.getPixel(1, 0))
        assertEquals(Color.BLUE, rotated.getPixel(0, 3))
        val mirrored = PhotoRenderer.render(source, PhotoEdit(mirror = true))
        assertEquals(Color.RED, mirrored.getPixel(3, 0))
        assertEquals(Color.BLUE, mirrored.getPixel(0, 1))
        source.recycle()
        rotated.recycle()
        mirrored.recycle()
    }

    @Test fun croppingCanReachEitherEdge() {
        val source = source()
        val left = PhotoRenderer.render(source, PhotoEdit(crop = CropAspect.SQUARE, positionX = -1f))
        val right = PhotoRenderer.render(source, PhotoEdit(crop = CropAspect.SQUARE, positionX = 1f))
        assertEquals(2, left.width)
        assertEquals(2, left.height)
        assertEquals(Color.RED, left.getPixel(0, 0))
        assertEquals(Color.BLUE, right.getPixel(1, 1))
        source.recycle()
        left.recycle()
        right.recycle()
    }

    @Test fun lightAndColorEditsChangeOutputButLeaveInputUntouched() {
        val source = source()
        val gray = PhotoRenderer.render(source, PhotoEdit(saturation = 0f))
        val bright = PhotoRenderer.render(source, PhotoEdit(brightness = 0.25f, warmth = 1f))
        val neutral = gray.getPixel(1, 0)
        assertEquals(Color.red(neutral), Color.green(neutral))
        assertEquals(Color.green(neutral), Color.blue(neutral))
        assertTrue(Color.red(bright.getPixel(1, 0)) > Color.red(source.getPixel(1, 0)))
        assertEquals(Color.RED, source.getPixel(0, 0))
        source.recycle()
        gray.recycle()
        bright.recycle()
    }
}
