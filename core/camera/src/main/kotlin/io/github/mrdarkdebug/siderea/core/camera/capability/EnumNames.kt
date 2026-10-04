package io.github.mrdarkdebug.siderea.core.camera.capability

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Camera2 describes modes as bare integers. This turns them into the constant names Google
 * documents (`MANUAL_SENSOR`, `CCT`, `NIGHT`...).
 *
 * Names are looked up from the platform's own public constants by prefix instead of a hand-written
 * table, so a mode added in a newer Android release is named correctly even if this app was
 * compiled against an older SDK, and a typo can't mislabel a capability.
 */
internal object EnumNames {
    private val cache = ConcurrentHashMap<Pair<Class<*>, String>, Map<Int, String>>()

    fun hardwareLevel(value: Int?) =
        nameOf(CameraMetadata::class.java, "INFO_SUPPORTED_HARDWARE_LEVEL_", value)
            .let { if (it.all(Char::isDigit)) "LEVEL_$it" else it }

    fun capability(value: Int) = nameOf(CameraMetadata::class.java, "REQUEST_AVAILABLE_CAPABILITIES_", value)

    fun aeMode(value: Int) = nameOf(CameraMetadata::class.java, "CONTROL_AE_MODE_", value)

    fun awbMode(value: Int) = nameOf(CameraMetadata::class.java, "CONTROL_AWB_MODE_", value)

    fun afMode(value: Int) = nameOf(CameraMetadata::class.java, "CONTROL_AF_MODE_", value)

    fun noiseReductionMode(value: Int) = nameOf(CameraMetadata::class.java, "NOISE_REDUCTION_MODE_", value)

    fun edgeMode(value: Int) = nameOf(CameraMetadata::class.java, "EDGE_MODE_", value)

    fun hotPixelMode(value: Int) = nameOf(CameraMetadata::class.java, "HOT_PIXEL_MODE_", value)

    fun colorCorrectionMode(value: Int) = nameOf(CameraMetadata::class.java, "COLOR_CORRECTION_MODE_", value)

    fun colorFilterArrangement(value: Int?) =
        nameOf(CameraMetadata::class.java, "SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_", value)

    fun focusCalibration(value: Int?) =
        nameOf(CameraMetadata::class.java, "LENS_INFO_FOCUS_DISTANCE_CALIBRATION_", value)

    fun opticalStabilization(value: Int) = nameOf(CameraMetadata::class.java, "LENS_OPTICAL_STABILIZATION_MODE_", value)

    fun timestampSource(value: Int?) = nameOf(CameraMetadata::class.java, "SENSOR_INFO_TIMESTAMP_SOURCE_", value)

    fun poseReference(value: Int?) = nameOf(CameraMetadata::class.java, "LENS_POSE_REFERENCE_", value)

    fun aePriorityMode(value: Int) = nameOf(CameraMetadata::class.java, "CONTROL_AE_PRIORITY_MODE_", value)

    @RequiresApi(Build.VERSION_CODES.S)
    fun extension(value: Int) = nameOf(CameraExtensionCharacteristics::class.java, "EXTENSION_", value)

    fun lensFacing(value: Int?): LensFacing =
        when (value) {
            CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
            CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
            CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
            else -> LensFacing.UNKNOWN
        }

    private fun nameOf(
        owner: Class<*>,
        prefix: String,
        value: Int?,
    ): String {
        if (value == null) return "UNKNOWN"
        val table =
            cache.getOrPut(owner to prefix) {
                owner.fields
                    .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
                    .filter { it.name.startsWith(prefix) }
                    .associate { it.getInt(null) to it.name.removePrefix(prefix) }
            }
        return table[value] ?: "UNKNOWN($value)"
    }
}
