package io.github.mrdarkdebug.siderea.core.camera.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraFormatTest {
    @Test
    fun `fast shutters render as fractions`() {
        assertEquals("1/8000 s", CameraFormat.exposure(125_000))
        assertEquals("1/250 s", CameraFormat.exposure(4_000_000))
        assertEquals("1/30 s", CameraFormat.exposure(33_333_333))
    }

    @Test
    fun `half a second is still a fraction`() {
        assertEquals("1/2 s", CameraFormat.exposure(500_000_000))
    }

    @Test
    fun `longer exposures render as seconds`() {
        assertEquals("0.8 s", CameraFormat.exposure(800_000_000))
        assertEquals("1 s", CameraFormat.exposure(1_000_000_000))
        assertEquals("2.5 s", CameraFormat.exposure(2_500_000_000))
        assertEquals("30 s", CameraFormat.exposure(30_000_000_000))
    }

    @Test
    fun `zero or negative exposure does not crash`() {
        assertEquals("0 s", CameraFormat.exposure(0))
        assertEquals("0 s", CameraFormat.exposure(-5))
    }

    @Test
    fun `frame durations convert to frame rates`() {
        assertEquals("30 fps", CameraFormat.frameRate(33_333_333))
        assertEquals("0.031 fps", CameraFormat.frameRate(32_000_000_000))
        assertEquals("n/a", CameraFormat.frameRate(0))
    }

    @Test
    fun `focus distances convert from diopters`() {
        assertNull(CameraFormat.diopterToMeters(0f))
        assertEquals(0.1f, CameraFormat.diopterToMeters(10f)!!, 1e-6f)
        assertEquals("∞", CameraFormat.distance(null))
        assertEquals("10 cm", CameraFormat.distance(0.1f))
        assertEquals("2 m", CameraFormat.distance(2f))
    }

    @Test
    fun `exposure compensation is signed`() {
        assertEquals("+1 EV", CameraFormat.ev(6, 1.0 / 6.0))
        assertEquals("−2 EV", CameraFormat.ev(-12, 1.0 / 6.0))
        assertEquals("0 EV", CameraFormat.ev(0, 1.0 / 6.0))
    }

    @Test
    fun `megapixels and sizes`() {
        assertEquals(49.94f, CameraFormat.megapixels(8160, 6120), 0.01f)
        assertEquals("4080×3060", CameraFormat.size(SizeInfo(4080, 3060)))
    }

    @Test
    fun `focal length and aperture labels drop trailing zeros`() {
        assertEquals("6.9 mm", CameraFormat.focal(6.9f))
        assertEquals("24 mm", CameraFormat.focal(24f))
        assertEquals("f/1.7", CameraFormat.aperture(1.7f))
        assertEquals("f/2", CameraFormat.aperture(2f))
    }

    @Test
    fun `numbers drop trailing zeros`() {
        assertEquals("12.9", CameraFormat.number(12.931958f, 1))
        assertEquals("1", CameraFormat.number(1.0f, 1))
        assertEquals("5.64", CameraFormat.number(5.6448f, 2))
    }
}
