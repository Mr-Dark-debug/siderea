package io.github.mrdarkdebug.siderea.core.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignmentTest {
    private val sky = SyntheticSky()
    private val reference = StarDetector.detect(sky.frame(noise = 4.0, seed = 1))

    private fun align(
        dx: Double,
        dy: Double,
        degrees: Double,
    ): AlignResult {
        val stars = StarDetector.detect(sky.frame(dx, dy, degrees, noise = 4.0, seed = 2))
        return checkNotNull(Alignment.estimate(reference, stars)) { "no alignment for $dx,$dy,$degrees" }
    }

    @Test
    fun recoversAPureShift() {
        // The frame is the sky moved by (+12.3, -7.8), so mapping it back onto the reference moves it by the opposite.
        val r = align(12.3, -7.8, 0.0)
        assertEquals(-12.3, r.transform.tx, 0.3)
        assertEquals(7.8, r.transform.ty, 0.3)
        assertEquals(0.0, r.transform.rotationDegrees, 0.05)
        assertTrue(r.rmsError < 0.5)
    }

    @Test
    fun recoversShiftAndRotation() {
        val r = align(-20.0, 15.0, 0.6)
        assertEquals(-0.6, r.transform.rotationDegrees, 0.05)
        assertEquals(1.0, r.transform.scale, 0.005)
        // Mapping any reference star through the transform's inverse must land on its place in the frame.
        assertTrue("only ${r.inliers} stars agreed", r.inliers >= 20)
    }

    @Test
    fun identicalFramesGiveAnIdentityTransform() {
        val r = align(0.0, 0.0, 0.0)
        assertEquals(0.0, r.transform.tx, 0.1)
        assertEquals(0.0, r.transform.ty, 0.1)
        assertEquals(0.0, r.transform.rotationDegrees, 0.02)
    }

    @Test
    fun aDifferentSkyDoesNotMatch() {
        val other = StarDetector.detect(SyntheticSky(seed = 99).frame(noise = 4.0, seed = 3))
        assertNull(Alignment.estimate(reference, other))
    }

    @Test
    fun tooFewStarsGiveNoResult() {
        assertNull(Alignment.estimate(reference, reference.take(3)))
        assertNull(Alignment.estimate(emptyList(), reference))
    }

    @Test
    fun transformMathIsConsistent() {
        val t = Transform(0.0, 1.0, 5.0, 0.0) // a quarter turn, then shift right
        assertEquals(5.0, t.mapX(1.0, 0.0), 1e-9)
        assertEquals(1.0, t.mapY(1.0, 0.0), 1e-9)
        assertEquals(90.0, t.rotationDegrees, 1e-9)
        assertEquals(1.0, t.scale, 1e-9)
        assertNotNull(Transform.IDENTITY)
        assertTrue(Transform.IDENTITY.isIdentity)
    }
}
