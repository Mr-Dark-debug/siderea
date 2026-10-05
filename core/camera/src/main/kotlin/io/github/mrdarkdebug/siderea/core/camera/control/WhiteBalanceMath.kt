// This file is colour science: published polynomial coefficients, matrices and CIE constants. Naming each
// number would hide the formulas they come from, so the magic-number rule is off for it.
@file:Suppress("MagicNumber")

package io.github.mrdarkdebug.siderea.core.camera.control

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A sensor's colour calibration as Camera2 reports it (`SENSOR_COLOR_TRANSFORM1/2` and the matching
 * `SENSOR_REFERENCE_ILLUMINANT1/2`). Matrices map CIE XYZ to the sensor's native RGB, row-major.
 */
class SensorCalibration(
    val illuminant1: Int,
    val colorTransform1: DoubleArray,
    val illuminant2: Int? = null,
    val colorTransform2: DoubleArray? = null,
)

/**
 * Manual white balance for cameras that only accept per-channel gains and a colour matrix.
 *
 * Android 16 added Kelvin and tint controls, but plenty of cameras, the Pixel 10 included, do not offer
 * them. For those, white balance is *computed*: pick the illuminant (a colour temperature plus a tint),
 * ask what the sensor would record for white under it (using the calibration the manufacturer ships), and
 * turn that into gains and a matrix. This is the same maths the DNG specification uses to interpret a
 * RAW file's colour, run in the other direction.
 */
object WhiteBalanceMath {
    class Solution(
        /** Gains for the red, green and blue channels, green normalised to 1. */
        val gainRed: Float,
        val gainGreen: Float,
        val gainBlue: Float,
        /** Row-major matrix from white-balanced sensor RGB to linear sRGB. */
        val transform: DoubleArray,
        /** False when no calibration was available and an approximation was used. */
        val calibrated: Boolean,
    ) {
        /** The four-channel gains Camera2 wants for an RGGB-ordered sensor. */
        fun rggbGains(): FloatArray = floatArrayOf(gainRed, gainGreen, gainGreen, gainBlue)
    }

    /**
     * @param kelvin colour temperature of the light to neutralise (2000 to 10000 is sensible).
     * @param tint green (negative) to magenta (positive), about -150 to +150.
     */
    fun solve(
        calibration: SensorCalibration?,
        kelvin: Int,
        tint: Int,
    ): Solution {
        val t = kelvin.toDouble().coerceIn(MIN_KELVIN, MAX_KELVIN)
        val (x0, y0) = cctToXy(t)
        val (x, y) = applyTint(x0, y0, tint)
        val white = xyToXyz(x, y)
        if (calibration == null) return approximate(t)

        val colorMatrix = matrixAt(calibration, t)
        val native = Matrix3.apply(colorMatrix, white)
        if (native.any { it <= MIN_CHANNEL }) return approximate(t)

        val gainRed = native[1] / native[0]
        val gainBlue = native[1] / native[2]
        // XYZ under the chosen light, adapted to D65, then to linear sRGB.
        val toXyz = Matrix3.inverse(colorMatrix)
        val undoGains = Matrix3.diagonal(1.0 / gainRed, 1.0, 1.0 / gainBlue)
        val adapt = bradford(white, D65_WHITE)
        val raw = Matrix3.multiply(XYZ_TO_SRGB, Matrix3.multiply(adapt, Matrix3.multiply(toXyz, undoGains)))
        // Scale so that white-balanced white (1, 1, 1) stays (1, 1, 1): exposure must not change with Kelvin.
        val whiteOut = Matrix3.apply(raw, doubleArrayOf(1.0, 1.0, 1.0))
        val scale = 3.0 / (whiteOut[0] + whiteOut[1] + whiteOut[2])
        val transform = DoubleArray(MATRIX_SIZE) { raw[it] * scale }
        return Solution(gainRed.toFloat(), 1f, gainBlue.toFloat(), transform, calibrated = true)
    }

    /** CIE 1931 xy of a blackbody (Kim et al. cubic-spline approximation, valid 1667 to 25000 K). */
    @Suppress("MagicNumber") // published polynomial coefficients
    fun cctToXy(kelvin: Double): Pair<Double, Double> {
        val t = kelvin.coerceIn(MIN_PLANCKIAN, MAX_PLANCKIAN)
        val x =
            if (t <= PLANCKIAN_SPLIT) {
                -0.2661239e9 / t.pow(3) - 0.2343589e6 / t.pow(2) + 0.8776956e3 / t + 0.179910
            } else {
                -3.0258469e9 / t.pow(3) + 2.1070379e6 / t.pow(2) + 0.2226347e3 / t + 0.240390
            }
        val y =
            when {
                t <= LOW_SPLIT -> -1.1063814 * x.pow(3) - 1.34811020 * x.pow(2) + 2.18555832 * x - 0.20219683
                t <= PLANCKIAN_SPLIT -> -0.9549476 * x.pow(3) - 1.37418593 * x.pow(2) + 2.09137015 * x - 0.16748867
                else -> 3.0817580 * x.pow(3) - 5.87338670 * x.pow(2) + 3.75112997 * x - 0.37001483
            }
        return x to y
    }

    /**
     * Shifts a chromaticity off the blackbody locus. Positive [tint] makes the *assumed light* greener,
     * so the resulting image is more magenta (as in a raw converter's tint slider).
     */
    fun applyTint(
        x: Double,
        y: Double,
        tint: Int,
    ): Pair<Double, Double> {
        if (tint == 0) return x to y
        val denominator = -2 * x + 12 * y + 3
        val u = 4 * x / denominator
        val v = 6 * y / denominator + tint * DUV_PER_TINT
        val newDenominator = 2 * u - 8 * v + 4
        return 3 * u / newDenominator to 2 * v / newDenominator
    }

    fun xyToXyz(
        x: Double,
        y: Double,
    ): DoubleArray = doubleArrayOf(x / y, 1.0, (1 - x - y) / y)

    /** Kelvin of an EXIF / Camera2 reference illuminant code. */
    fun illuminantKelvin(code: Int): Double =
        when (code) {
            LIGHT_DAYLIGHT, LIGHT_FINE_WEATHER -> 5500.0
            LIGHT_FLUORESCENT -> 4150.0
            LIGHT_TUNGSTEN -> 2850.0
            LIGHT_FLASH -> 5500.0
            LIGHT_CLOUDY -> 6500.0
            LIGHT_SHADE -> 7500.0
            LIGHT_DAYLIGHT_FLUORESCENT -> 6430.0
            LIGHT_DAY_WHITE_FLUORESCENT -> 5000.0
            LIGHT_COOL_WHITE_FLUORESCENT -> 4200.0
            LIGHT_WHITE_FLUORESCENT -> 3500.0
            LIGHT_STANDARD_A -> 2856.0
            LIGHT_STANDARD_B -> 4874.0
            LIGHT_STANDARD_C -> 6774.0
            LIGHT_D55 -> 5503.0
            LIGHT_D65 -> 6504.0
            LIGHT_D75 -> 7504.0
            LIGHT_D50 -> 5003.0
            LIGHT_ISO_TUNGSTEN -> 3200.0
            else -> DEFAULT_ILLUMINANT_KELVIN
        }

    /** `CCT -> camera`: the calibration matrix at a colour temperature, interpolated in mired (1 / K). */
    internal fun matrixAt(
        calibration: SensorCalibration,
        kelvin: Double,
    ): DoubleArray {
        val second = calibration.colorTransform2
        val code2 = calibration.illuminant2
        if (second == null || code2 == null) return calibration.colorTransform1
        val k1 = illuminantKelvin(calibration.illuminant1)
        val k2 = illuminantKelvin(code2)
        if (k1 == k2) return calibration.colorTransform1
        val low =
            if (k1 < k2) {
                Quad(k1, calibration.colorTransform1, k2, second)
            } else {
                Quad(k2, second, k1, calibration.colorTransform1)
            }
        val lowK = low.lowK
        val highK = low.highK
        val weight = ((1 / kelvin - 1 / lowK) / (1 / highK - 1 / lowK)).coerceIn(0.0, 1.0)
        return Matrix3.blend(low.lowM, low.highM, weight)
    }

    /** Bradford chromatic adaptation from a source white to a destination white (both XYZ, Y = 1). */
    internal fun bradford(
        source: DoubleArray,
        destination: DoubleArray,
    ): DoubleArray {
        val sourceCone = Matrix3.apply(BRADFORD, source)
        val destinationCone = Matrix3.apply(BRADFORD, destination)
        val scale =
            Matrix3.diagonal(
                destinationCone[0] / sourceCone[0],
                destinationCone[1] / sourceCone[1],
                destinationCone[2] / sourceCone[2],
            )
        return Matrix3.multiply(Matrix3.inverse(BRADFORD), Matrix3.multiply(scale, BRADFORD))
    }

    /** Used only when the camera reports no calibration: a plausible daylight-relative approximation. */
    private fun approximate(kelvin: Double): Solution {
        val red = (kelvin / REFERENCE_KELVIN).pow(APPROX_RED_EXPONENT).coerceIn(APPROX_MIN, APPROX_MAX)
        val blue = (REFERENCE_KELVIN / kelvin).pow(APPROX_BLUE_EXPONENT).coerceIn(APPROX_MIN, APPROX_MAX)
        return Solution(red.toFloat(), 1f, blue.toFloat(), Matrix3.IDENTITY.copyOf(), calibrated = false)
    }

    /** Camera2 wants the matrix as 9 rationals, numerator then denominator. */
    fun toRationals(
        matrix: DoubleArray,
        denominator: Int = RATIONAL_DENOMINATOR,
    ): IntArray =
        IntArray(MATRIX_SIZE * 2) { i ->
            val value = matrix[i / 2]
            if (i % 2 == 0) (value * denominator).roundToInt() else denominator
        }

    private data class Quad(
        val lowK: Double,
        val lowM: DoubleArray,
        val highK: Double,
        val highM: DoubleArray,
    )

    private const val MATRIX_SIZE = 9
    private const val RATIONAL_DENOMINATOR = 10_000
    private const val MIN_KELVIN = 1700.0
    private const val MAX_KELVIN = 25000.0
    private const val MIN_PLANCKIAN = 1667.0
    private const val MAX_PLANCKIAN = 25000.0
    private const val LOW_SPLIT = 2222.0
    private const val PLANCKIAN_SPLIT = 4000.0
    private const val MIN_CHANNEL = 1e-6
    private const val DUV_PER_TINT = 0.0002
    private const val DEFAULT_ILLUMINANT_KELVIN = 6500.0
    private const val REFERENCE_KELVIN = 6500.0
    private const val APPROX_RED_EXPONENT = 0.55
    private const val APPROX_BLUE_EXPONENT = 0.7
    private const val APPROX_MIN = 0.3
    private const val APPROX_MAX = 4.0

    private const val LIGHT_DAYLIGHT = 1
    private const val LIGHT_FLUORESCENT = 2
    private const val LIGHT_TUNGSTEN = 3
    private const val LIGHT_FLASH = 4
    private const val LIGHT_FINE_WEATHER = 9
    private const val LIGHT_CLOUDY = 10
    private const val LIGHT_SHADE = 11
    private const val LIGHT_DAYLIGHT_FLUORESCENT = 12
    private const val LIGHT_DAY_WHITE_FLUORESCENT = 13
    private const val LIGHT_COOL_WHITE_FLUORESCENT = 14
    private const val LIGHT_WHITE_FLUORESCENT = 15
    private const val LIGHT_STANDARD_A = 17
    private const val LIGHT_STANDARD_B = 18
    private const val LIGHT_STANDARD_C = 19
    private const val LIGHT_D55 = 20
    private const val LIGHT_D65 = 21
    private const val LIGHT_D75 = 22
    private const val LIGHT_D50 = 23
    private const val LIGHT_ISO_TUNGSTEN = 24

    private val D65_WHITE = doubleArrayOf(0.95047, 1.0, 1.08883)

    private val BRADFORD =
        doubleArrayOf(
            0.8951,
            0.2664,
            -0.1614,
            -0.7502,
            1.7135,
            0.0367,
            0.0389,
            -0.0685,
            1.0296,
        )

    /** CIE XYZ (D65) to linear sRGB. */
    internal val XYZ_TO_SRGB =
        doubleArrayOf(
            3.2404542,
            -1.5371385,
            -0.4985314,
            -0.9692660,
            1.8760108,
            0.0415560,
            0.0556434,
            -0.2040259,
            1.0572252,
        )
}
