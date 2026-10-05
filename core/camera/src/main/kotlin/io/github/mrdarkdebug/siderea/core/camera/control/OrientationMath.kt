package io.github.mrdarkdebug.siderea.core.camera.control

import kotlin.math.atan2
import kotlin.math.hypot

/** Device-orientation and levelling maths. Pure, so the sign conventions are unit-tested. */
object OrientationMath {
    private const val FULL_TURN = 360
    private const val QUARTER_TURN = 90
    private const val HALF_TURN = 180
    private const val THREE_QUARTER_TURN = 270
    private const val HALF_QUARTER = 45

    /**
     * The `JPEG_ORIENTATION` for a capture, as the Camera2 documentation prescribes.
     *
     * @param sensorOrientation `SENSOR_ORIENTATION` of the camera (0, 90, 180 or 270).
     * @param deviceOrientationDegrees clockwise rotation of the device from its natural upright position,
     *        as given by `OrientationEventListener` (any value; it is rounded to the nearest 90).
     */
    fun jpegOrientation(
        sensorOrientation: Int,
        deviceOrientationDegrees: Int,
        frontFacing: Boolean,
    ): Int {
        var device = ((deviceOrientationDegrees + HALF_QUARTER).mod(FULL_TURN) / QUARTER_TURN) * QUARTER_TURN
        if (frontFacing) device = -device
        return (sensorOrientation + device + FULL_TURN).mod(FULL_TURN)
    }

    /** EXIF orientation tag value for a clockwise rotation, as used for DNG files. */
    fun exifOrientation(degrees: Int): Int =
        when (degrees.mod(FULL_TURN)) {
            QUARTER_TURN -> EXIF_ROTATE_90
            HALF_TURN -> EXIF_ROTATE_180
            THREE_QUARTER_TURN -> EXIF_ROTATE_270
            else -> EXIF_NORMAL
        }

    /**
     * Roll of the device in degrees from gravity (x right, y up, z out of the screen; the sensor frame
     * of `TYPE_GRAVITY` or `TYPE_ACCELEROMETER`). Zero when the phone is upright in portrait; the result
     * is in -180..180.
     */
    fun rollDegrees(
        gravityX: Float,
        gravityY: Float,
    ): Float = Math.toDegrees(atan2(gravityX.toDouble(), gravityY.toDouble())).toFloat()

    /** Distance, in degrees, from the nearest of the four upright positions. -45..45; 0 is level. */
    fun horizonErrorDegrees(rollDegrees: Float): Float {
        val shifted = (rollDegrees + HALF_QUARTER).mod(QUARTER_TURN.toFloat())
        return shifted - HALF_QUARTER
    }

    /**
     * How far the back camera's optical axis points above the horizon: 0 held upright, +90 when the
     * camera faces the sky (phone lying screen-down), -90 when it faces the ground (phone lying screen-up).
     */
    fun cameraElevationDegrees(
        gravityX: Float,
        gravityY: Float,
        gravityZ: Float,
    ): Float = Math.toDegrees(atan2(-gravityZ.toDouble(), hypot(gravityX.toDouble(), gravityY.toDouble()))).toFloat()

    const val EXIF_NORMAL = 1
    const val EXIF_ROTATE_180 = 3
    const val EXIF_ROTATE_90 = 6
    const val EXIF_ROTATE_270 = 8
}
