package io.github.mrdarkdebug.siderea.core.camera.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteBalanceMathTest {
    /** A sensor whose native colour space is linear sRGB: the simplest possible calibration. */
    private val srgbSensor =
        SensorCalibration(
            illuminant1 = 21, // D65
            colorTransform1 = WhiteBalanceMath.XYZ_TO_SRGB,
            illuminant2 = 17, // Standard illuminant A
            colorTransform2 = WhiteBalanceMath.XYZ_TO_SRGB,
        )

    @Test
    fun `blackbody chromaticity matches published values`() {
        val (x6500, y6500) = WhiteBalanceMath.cctToXy(6500.0)
        assertEquals(0.3135, x6500, 0.002)
        assertEquals(0.3237, y6500, 0.002)
        // CIE standard illuminant A is a 2856 K blackbody: (0.44757, 0.40745).
        val (xa, ya) = WhiteBalanceMath.cctToXy(2856.0)
        assertEquals(0.44757, xa, 0.002)
        assertEquals(0.40745, ya, 0.002)
    }

    @Test
    fun `daylight needs almost no correction`() {
        val s = WhiteBalanceMath.solve(srgbSensor, kelvin = 6500, tint = 0)
        assertTrue(s.calibrated)
        assertEquals(1f, s.gainRed, 0.07f)
        assertEquals(1f, s.gainBlue, 0.07f)
        assertEquals(1f, s.gainGreen, 0f)
    }

    @Test
    fun `tungsten light gets less red and much more blue gain`() {
        val s = WhiteBalanceMath.solve(srgbSensor, kelvin = 2856, tint = 0)
        // Worked out by hand for illuminant A on an sRGB sensor: r = 0.448, b = 3.54.
        assertEquals(0.448f, s.gainRed, 0.03f)
        assertEquals(3.54f, s.gainBlue, 0.25f)
    }

    @Test
    fun `gains move monotonically with colour temperature`() {
        val kelvins = listOf(2500, 3200, 4500, 5500, 6500, 8000, 10000)
        val gains = kelvins.map { WhiteBalanceMath.solve(srgbSensor, it, 0) }
        gains.zipWithNext().forEach { (a, b) ->
            assertTrue("red gain rises with Kelvin", b.gainRed > a.gainRed)
            assertTrue("blue gain falls with Kelvin", b.gainBlue < a.gainBlue)
        }
    }

    @Test
    fun `positive tint makes the image more magenta by raising red and blue gain`() {
        val magenta = WhiteBalanceMath.solve(srgbSensor, 5500, 60)
        val neutral = WhiteBalanceMath.solve(srgbSensor, 5500, 0)
        val green = WhiteBalanceMath.solve(srgbSensor, 5500, -60)
        assertTrue(magenta.gainRed > neutral.gainRed && neutral.gainRed > green.gainRed)
        assertTrue(magenta.gainBlue > neutral.gainBlue && neutral.gainBlue > green.gainBlue)
    }

    @Test
    fun `the colour matrix keeps white as white and exposure unchanged`() {
        listOf(2500, 4000, 5500, 7500).forEach { kelvin ->
            val s = WhiteBalanceMath.solve(srgbSensor, kelvin, 0)
            val white = Matrix3.apply(s.transform, doubleArrayOf(1.0, 1.0, 1.0))
            white.forEach { assertEquals("white channel at $kelvin K", 1.0, it, 1e-6) }
        }
    }

    @Test
    fun `matrix interpolation follows mired between the two calibration illuminants`() {
        val a = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val b = doubleArrayOf(2.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 2.0)
        val cal = SensorCalibration(illuminant1 = 21, colorTransform1 = b, illuminant2 = 17, colorTransform2 = a)
        assertEquals(2.0, WhiteBalanceMath.matrixAt(cal, 6504.0)[0], 1e-9)
        assertEquals(1.0, WhiteBalanceMath.matrixAt(cal, 2856.0)[0], 1e-9)
        assertEquals(1.0, WhiteBalanceMath.matrixAt(cal, 2000.0)[0], 1e-9)
        assertEquals(2.0, WhiteBalanceMath.matrixAt(cal, 9000.0)[0], 1e-9)
        val mid = WhiteBalanceMath.matrixAt(cal, 4000.0)[0]
        assertTrue(mid > 1.0 && mid < 2.0)
    }

    @Test
    fun `a single calibration illuminant is used as is`() {
        val cal = SensorCalibration(illuminant1 = 21, colorTransform1 = WhiteBalanceMath.XYZ_TO_SRGB)
        assertTrue(WhiteBalanceMath.solve(cal, 5000, 0).calibrated)
    }

    @Test
    fun `without calibration a plausible approximation is used and flagged`() {
        val warm = WhiteBalanceMath.solve(null, 2800, 0)
        assertFalse(warm.calibrated)
        assertTrue(warm.gainRed < 1f && warm.gainBlue > 1f)
        assertEquals(1f, WhiteBalanceMath.solve(null, 6500, 0).gainRed, 0.01f)
    }

    @Test
    fun `rggb gains repeat green`() {
        val s = WhiteBalanceMath.solve(srgbSensor, 5000, 0)
        val g = s.rggbGains()
        assertEquals(4, g.size)
        assertEquals(g[1], g[2], 0f)
        assertEquals(s.gainRed, g[0], 0f)
        assertEquals(s.gainBlue, g[3], 0f)
    }

    @Test
    fun `rationals encode the matrix for camera2`() {
        val r = WhiteBalanceMath.toRationals(doubleArrayOf(1.5, 0.0, -0.25, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))
        assertEquals(18, r.size)
        assertEquals(15_000, r[0])
        assertEquals(10_000, r[1])
        assertEquals(-2_500, r[4])
    }

    @Test
    fun `illuminant codes map to kelvin`() {
        assertEquals(6504.0, WhiteBalanceMath.illuminantKelvin(21), 0.0)
        assertEquals(2856.0, WhiteBalanceMath.illuminantKelvin(17), 0.0)
        assertEquals(6500.0, WhiteBalanceMath.illuminantKelvin(255), 0.0)
    }

    @Test
    fun `matrix inverse round trips`() {
        val m = doubleArrayOf(2.0, 1.0, 0.0, 0.0, 3.0, 1.0, 1.0, 0.0, 4.0)
        val product = Matrix3.multiply(m, Matrix3.inverse(m))
        for (i in 0 until 9) assertEquals(Matrix3.IDENTITY[i], product[i], 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a singular matrix is rejected`() {
        Matrix3.inverse(DoubleArray(9))
    }
}
