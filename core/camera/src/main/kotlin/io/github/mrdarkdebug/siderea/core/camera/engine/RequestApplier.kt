package io.github.mrdarkdebug.siderea.core.camera.engine

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Writes a [RequestPlan] into a `CaptureRequest.Builder`.
 *
 * When the streams are bound to a physical lens, sensor-level settings are also set for that physical
 * camera, because a logical camera's request-level values are not guaranteed to reach a specific sensor.
 */
internal class RequestApplier(
    private val chars: CameraCharacteristics,
    private val physicalId: String?,
) {
    private val physicalKeys: Set<String> =
        if (physicalId == null) {
            emptySet()
        } else {
            runCatching { chars.availablePhysicalCameraRequestKeys.orEmpty().mapTo(HashSet()) { it.name } }
                .getOrDefault(emptySet())
        }

    fun apply(
        builder: CaptureRequest.Builder,
        plan: RequestPlan,
        zoomRatio: Float?,
    ) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        if (zoomRatio != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
        }
        applyExposure(builder, plan)
        applyFocus(builder, plan)
        applyWhiteBalance(builder, plan.wb)
    }

    private fun applyExposure(
        builder: CaptureRequest.Builder,
        plan: RequestPlan,
    ) {
        if (plan.autoExposure) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, plan.exposureCompensationSteps)
            return
        }
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
        setBoth(builder, CaptureRequest.SENSOR_EXPOSURE_TIME, plan.shutterNs)
        setBoth(builder, CaptureRequest.SENSOR_SENSITIVITY, plan.iso)
        setBoth(builder, CaptureRequest.SENSOR_FRAME_DURATION, plan.frameDurationNs)
    }

    private fun applyFocus(
        builder: CaptureRequest.Builder,
        plan: RequestPlan,
    ) {
        val mode =
            when (plan.afMode) {
                "CONTINUOUS_PICTURE" -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                "AUTO" -> CameraMetadata.CONTROL_AF_MODE_AUTO
                else -> CameraMetadata.CONTROL_AF_MODE_OFF
            }
        builder.set(CaptureRequest.CONTROL_AF_MODE, mode)
        plan.focusDiopters?.let { setBoth(builder, CaptureRequest.LENS_FOCUS_DISTANCE, it) }
    }

    private fun applyWhiteBalance(
        builder: CaptureRequest.Builder,
        wb: WbPlan,
    ) {
        when (wb) {
            WbPlan.Auto -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_FAST)
            }

            is WbPlan.Preset -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, awbConstant(wb.modeName))
            }

            is WbPlan.Gains -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
                builder.set(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
                )
                val g = wb.rggb
                builder.set(
                    CaptureRequest.COLOR_CORRECTION_GAINS,
                    RggbChannelVector(g[RED], g[GREEN_EVEN], g[GREEN_ODD], g[BLUE]),
                )
                builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, ColorSpaceTransform(wb.transformRationals))
            }

            is WbPlan.Cct -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
                if (Build.VERSION.SDK_INT >= ANDROID_16) applyCct(builder, wb)
            }
        }
    }

    @RequiresApi(ANDROID_16)
    private fun applyCct(
        builder: CaptureRequest.Builder,
        wb: WbPlan.Cct,
    ) {
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_CCT)
        builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE, wb.kelvin)
        builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TINT, wb.tint)
    }

    private fun <T> setBoth(
        builder: CaptureRequest.Builder,
        key: CaptureRequest.Key<T>,
        value: T,
    ) {
        builder.set(key, value)
        val id = physicalId
        if (id != null && key.name in physicalKeys) {
            runCatching { builder.setPhysicalCameraKey(key, value, id) }
        }
    }

    private fun awbConstant(name: String): Int =
        when (name) {
            "DAYLIGHT" -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
            "CLOUDY_DAYLIGHT" -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            "INCANDESCENT" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
            "FLUORESCENT" -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
            "SHADE" -> CameraMetadata.CONTROL_AWB_MODE_SHADE
            else -> CameraMetadata.CONTROL_AWB_MODE_AUTO
        }

    private companion object {
        const val ANDROID_16 = 36
        const val RED = 0
        const val GREEN_EVEN = 1
        const val GREEN_ODD = 2
        const val BLUE = 3
    }
}
