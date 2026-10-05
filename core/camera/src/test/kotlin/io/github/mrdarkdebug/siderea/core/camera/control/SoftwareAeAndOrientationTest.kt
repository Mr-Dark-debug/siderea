package io.github.mrdarkdebug.siderea.core.camera.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class SoftwareAeAndOrientationTest {
    private val limits = Pixel10Fixture.limits("0")

    @Test
    fun `a dark scene raises iso in shutter priority`() {
        val step = SoftwareAe.step(40f, 8_000_000L, 100, shutterPriority = true, limits = limits)
        assertEquals(8_000_000L, step.shutterNs)
        assertTrue(step.iso > 100)
    }

    @Test
    fun `a bright scene lowers iso in shutter priority`() {
        val step = SoftwareAe.step(220f, 8_000_000L, 800, shutterPriority = true, limits = limits)
        assertTrue(step.iso < 800)
    }

    @Test
    fun `iso priority moves the shutter instead`() {
        val step = SoftwareAe.step(40f, 8_000_000L, 400, shutterPriority = false, limits = limits)
        assertEquals(400, step.iso)
        assertTrue(step.shutterNs > 8_000_000L)
    }

    @Test
    fun `near the target nothing changes`() {
        val step = SoftwareAe.step(SoftwareAe.TARGET_LUMA + 3, 8_000_000L, 200, shutterPriority = true, limits = limits)
        assertEquals(200, step.iso)
        assertFalse(step.limited)
    }

    @Test
    fun `a single step never moves more than one stop`() {
        val step = SoftwareAe.step(1f, 8_000_000L, 100, shutterPriority = true, limits = limits)
        assertTrue(step.iso <= 200)
    }

    @Test
    fun `pinned at the iso ceiling it reports it is limited`() {
        val step = SoftwareAe.step(10f, 8_000_000L, limits.isoMax, shutterPriority = true, limits = limits)
        assertEquals(limits.isoMax, step.iso)
        assertTrue(step.limited)
    }

    @Test
    fun `positive bias aims for a brighter picture`() {
        val neutral = SoftwareAe.step(100f, 8_000_000L, 200, true, limits, bias = 0f)
        val brighter = SoftwareAe.step(100f, 8_000_000L, 200, true, limits, bias = 1f)
        assertTrue(brighter.iso > neutral.iso)
    }

    @Test
    fun `the loop converges on a simulated scene without hunting`() {
        // Scene brightness grows with shutter * iso; display luma is gamma encoded.
        fun luma(
            shutterNs: Long,
            iso: Int,
        ): Float {
            val linear = (shutterNs / 1e9) * iso / 6.0
            return (255 * linear.coerceIn(0.0, 1.0).pow(1 / 2.2)).toFloat()
        }
        var iso = 50
        val shutter = 4_000_000L
        val history = mutableListOf<Int>()
        repeat(25) {
            val step = SoftwareAe.step(luma(shutter, iso), shutter, iso, shutterPriority = true, limits = limits)
            iso = step.iso
            history += iso
        }
        assertTrue(abs(SoftwareAe.TARGET_LUMA - luma(shutter, iso)) <= SoftwareAe.DEAD_BAND + 2)
        assertEquals(history[history.size - 1], history[history.size - 2])
    }

    @Test
    fun `jpeg orientation follows the camera2 formula`() {
        assertEquals(90, OrientationMath.jpegOrientation(90, 0, frontFacing = false))
        assertEquals(180, OrientationMath.jpegOrientation(90, 90, frontFacing = false))
        assertEquals(0, OrientationMath.jpegOrientation(90, 270, frontFacing = false))
        assertEquals(270, OrientationMath.jpegOrientation(270, 0, frontFacing = true))
        assertEquals(180, OrientationMath.jpegOrientation(270, 90, frontFacing = true))
        assertEquals(90, OrientationMath.jpegOrientation(90, 350, frontFacing = false))
        assertEquals(180, OrientationMath.jpegOrientation(90, 80, frontFacing = false))
    }

    @Test
    fun `exif orientation codes`() {
        assertEquals(OrientationMath.EXIF_NORMAL, OrientationMath.exifOrientation(0))
        assertEquals(OrientationMath.EXIF_ROTATE_90, OrientationMath.exifOrientation(90))
        assertEquals(OrientationMath.EXIF_ROTATE_180, OrientationMath.exifOrientation(180))
        assertEquals(OrientationMath.EXIF_ROTATE_270, OrientationMath.exifOrientation(270))
        assertEquals(OrientationMath.EXIF_ROTATE_90, OrientationMath.exifOrientation(-270))
    }

    @Test
    fun `roll is zero upright and tracks tilt either way`() {
        assertEquals(0f, OrientationMath.rollDegrees(0f, 9.8f), 0.01f)
        assertEquals(90f, OrientationMath.rollDegrees(9.8f, 0f), 0.01f)
        assertEquals(-90f, OrientationMath.rollDegrees(-9.8f, 0f), 0.01f)
        assertEquals(180f, abs(OrientationMath.rollDegrees(0f, -9.8f)), 0.01f)
    }

    @Test
    fun `horizon error is distance from the nearest upright position`() {
        assertEquals(0f, OrientationMath.horizonErrorDegrees(0f), 0.001f)
        assertEquals(3f, OrientationMath.horizonErrorDegrees(3f), 0.001f)
        assertEquals(-3f, OrientationMath.horizonErrorDegrees(87f), 0.001f)
        assertEquals(2f, OrientationMath.horizonErrorDegrees(92f), 0.001f)
        assertEquals(0f, OrientationMath.horizonErrorDegrees(-90f), 0.001f)
        assertEquals(0f, OrientationMath.horizonErrorDegrees(180f), 0.001f)
    }

    @Test
    fun `camera elevation reads the sky and the ground`() {
        assertEquals(0f, OrientationMath.cameraElevationDegrees(0f, 9.8f, 0f), 0.01f)
        assertEquals(90f, OrientationMath.cameraElevationDegrees(0f, 0f, -9.8f), 0.01f)
        assertEquals(-90f, OrientationMath.cameraElevationDegrees(0f, 0f, 9.8f), 0.01f)
    }
}
