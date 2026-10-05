package io.github.mrdarkdebug.siderea.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import io.github.mrdarkdebug.siderea.core.camera.control.OrientationMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How the phone is held right now. */
data class MotionState(
    /** Clockwise rotation from natural portrait, 0..359, for photo orientation. */
    val deviceOrientation: Int = 0,
    /** Roll from gravity, -180..180; 0 when upright in portrait. */
    val rollDegrees: Float = 0f,
    /** Distance from the nearest upright position, for the horizon level. */
    val horizonErrorDegrees: Float = 0f,
    /** Angle of the back camera's axis above the horizon (+90 at the sky). */
    val elevationDegrees: Float = 0f,
    val hasGravitySensor: Boolean = true,
)

/**
 * Orientation and tilt of the phone, fed by the gravity sensor. Only active between [start] and [stop],
 * so the camera screen pays for sensors only while it is on screen.
 */
@Singleton
class DeviceMotion
    @Inject
    constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    ) {
        private val mutableState = MutableStateFlow(MotionState())
        val state: StateFlow<MotionState> = mutableState.asStateFlow()

        private val sensorManager = context.getSystemService(SensorManager::class.java)
        private val orientationListener =
            object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    mutableState.value = mutableState.value.copy(deviceOrientation = orientation)
                }
            }
        private var smoothed: FloatArray? = null
        private var started = false

        private val sensorListener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val previous = smoothed
                    val g =
                        if (previous == null) {
                            event.values.copyOf()
                        } else {
                            FloatArray(AXES) { previous[it] + SMOOTHING * (event.values[it] - previous[it]) }
                        }
                    smoothed = g
                    val roll = OrientationMath.rollDegrees(g[0], g[1])
                    mutableState.value =
                        mutableState.value.copy(
                            rollDegrees = roll,
                            horizonErrorDegrees = OrientationMath.horizonErrorDegrees(roll),
                            elevationDegrees = OrientationMath.cameraElevationDegrees(g[0], g[1], g[2]),
                        )
                }

                override fun onAccuracyChanged(
                    sensor: Sensor,
                    accuracy: Int,
                ) = Unit
            }

        fun start() {
            if (started) return
            started = true
            if (orientationListener.canDetectOrientation()) orientationListener.enable()
            val sensor =
                sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
                    ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor == null) {
                mutableState.value = mutableState.value.copy(hasGravitySensor = false)
            } else {
                sensorManager?.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }

        fun stop() {
            if (!started) return
            started = false
            orientationListener.disable()
            sensorManager?.unregisterListener(sensorListener)
            smoothed = null
        }

        private companion object {
            const val AXES = 3
            const val SMOOTHING = 0.2f
        }
    }
