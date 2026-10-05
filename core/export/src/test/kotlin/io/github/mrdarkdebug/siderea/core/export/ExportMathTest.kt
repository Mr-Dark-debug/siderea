package io.github.mrdarkdebug.siderea.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportMathTest {
    @Test
    fun fullFrameKeepsEverythingButOddPixels() {
        val rect = ExportMath.cropRect(4001, 3001, CropAspect.FULL)
        assertEquals(PixelRect(0, 0, 4000, 3000), rect)
    }

    @Test
    fun wideCropOfAFourThreeFrameTrimsTheHeight() {
        val rect = ExportMath.cropRect(4000, 3000, CropAspect.WIDE)
        assertEquals(4000, rect.width)
        assertEquals(2250, rect.height)
        assertEquals(0, rect.left)
        // Centred: half of the 750 spare rows, kept even.
        assertEquals(374, rect.top)
    }

    @Test
    fun panMovesTheWindowButNeverOutsideTheFrame() {
        val top = ExportMath.cropRect(4000, 3000, CropAspect.WIDE, panY = 0f)
        val bottom = ExportMath.cropRect(4000, 3000, CropAspect.WIDE, panY = 1f)
        assertEquals(0, top.top)
        assertEquals(3000, bottom.bottom)
        val beyond = ExportMath.cropRect(4000, 3000, CropAspect.WIDE, panY = 9f)
        assertEquals(bottom, beyond)
    }

    @Test
    fun squareCropOfAPortraitFrameTrimsTheHeight() {
        val rect = ExportMath.cropRect(3000, 4000, CropAspect.SQUARE)
        assertEquals(3000, rect.width)
        assertEquals(3000, rect.height)
    }

    @Test
    fun tallCropOfALandscapeFrameTrimsTheWidth() {
        val rect = ExportMath.cropRect(4000, 3000, CropAspect.TALL)
        assertEquals(3000, rect.height)
        assertEquals(1688, rect.width)
    }

    @Test
    fun outputIsScaledDownToTheLongSideAndNeverUp() {
        assertEquals(PixelSize(1920, 1440), ExportMath.outputSize(4000, 3000, OutputSize.FHD))
        assertEquals(PixelSize(1440, 1920), ExportMath.outputSize(3000, 4000, OutputSize.FHD))
        assertEquals(PixelSize(1280, 960), ExportMath.outputSize(1280, 960, OutputSize.UHD))
        assertEquals(PixelSize(4000, 3000), ExportMath.outputSize(4000, 3000, OutputSize.SOURCE))
    }

    @Test
    fun outputSidesAreAlwaysEven() {
        listOf(OutputSize.UHD, OutputSize.FHD, OutputSize.HD, OutputSize.SOURCE).forEach { size ->
            val out = ExportMath.outputSize(4033, 3001, size)
            assertEquals(0, out.width % 2)
            assertEquals(0, out.height % 2)
        }
    }

    @Test
    fun bitrateScalesWithPixelsFpsAndQualityWithinLimits() {
        val size = PixelSize(1920, 1080)
        val good = ExportMath.bitrate(size, VideoSpec(fps = 30))
        val best = ExportMath.bitrate(size, VideoSpec(fps = 30, quality = VideoQuality.BEST))
        val hevc = ExportMath.bitrate(size, VideoSpec(codec = VideoCodec.HEVC, fps = 30))
        assertTrue(best > good)
        assertTrue(hevc < good)
        assertEquals(500_000, ExportMath.bitrate(PixelSize(64, 64), VideoSpec(fps = 1)))
        assertEquals(
            100_000_000,
            ExportMath.bitrate(PixelSize(8192, 8192), VideoSpec(fps = 60, quality = VideoQuality.BEST)),
        )
    }

    @Test
    fun durationAndSizeEstimates() {
        assertEquals(10.0, ExportMath.videoSeconds(300, 30), 1e-9)
        assertEquals(0.0, ExportMath.videoSeconds(300, 0), 1e-9)
        val spec = VideoSpec(fps = 30)
        val size = PixelSize(1920, 1080)
        val bytes = ExportMath.estimatedBytes(300, size, spec)
        assertEquals((ExportMath.bitrate(size, spec) / 8L * 10L).toDouble(), bytes.toDouble(), 10.0)
    }

    @Test
    fun fitToEncoderShrinksUntilAccepted() {
        val fitted = ExportMath.fitToEncoder(PixelSize(4000, 3000)) { it.width <= 1920 }
        assertNotNull(fitted)
        assertTrue(fitted!!.width <= 1920)
        assertEquals(0, fitted.width % 2)
        // The aspect ratio survives the shrinking (to within rounding to even pixels).
        assertEquals(4.0 / 3.0, fitted.width.toDouble() / fitted.height, 0.01)
    }

    @Test
    fun fitToEncoderGivesUpWhenNothingFits() {
        assertNull(ExportMath.fitToEncoder(PixelSize(4000, 3000)) { false })
    }
}
