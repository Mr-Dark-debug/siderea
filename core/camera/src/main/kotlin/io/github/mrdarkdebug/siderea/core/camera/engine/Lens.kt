package io.github.mrdarkdebug.siderea.core.camera.engine

import io.github.mrdarkdebug.siderea.core.camera.capability.CameraInfo
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraKind
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityReport
import io.github.mrdarkdebug.siderea.core.camera.capability.LensFacing

/**
 * One lens the user can pick, as the viewfinder sees it.
 *
 * @property openId the camera id passed to `CameraManager.openCamera`: a standalone camera, or the logical
 *           camera that fronts several physical lenses.
 * @property physicalId when set, the streams are bound to this physical lens of the logical camera, which
 *           is how a specific sensor's RAW is obtained. Null means "whatever the camera's default is".
 */
data class Lens(
    val key: String,
    val openId: String,
    val physicalId: String?,
    val facing: LensFacing,
    val zoomLabel: String,
    /** Characteristics of this lens (the physical camera's own when there is one). */
    val info: CameraInfo,
    /** The logical camera's characteristics when [physicalId] is set; used as a fallback. */
    val logical: CameraInfo?,
    /** Ratio to the main lens, used to emulate this lens by zooming when physical streams fail. */
    val zoomRatio: Float,
) {
    val sensorOrientation: Int get() = info.sensor.orientationDegrees ?: DEFAULT_ORIENTATION
    val isFront: Boolean get() = facing == LensFacing.FRONT

    private companion object {
        const val DEFAULT_ORIENTATION = 90
    }
}

/** Builds the list of lenses from the capability report: only lenses that really exist. */
object LensCatalog {
    fun from(report: CapabilityReport): List<Lens> {
        val readable = report.cameras.filter { it.error == null }
        val byId = readable.associateBy { it.id }
        val lenses = mutableListOf<Lens>()
        val claimed = mutableSetOf<String>()

        for (camera in readable) {
            when {
                camera.kind == CameraKind.LOGICAL && camera.physicalIds.isNotEmpty() -> {
                    val children = camera.physicalIds.mapNotNull(byId::get)
                    claimed += camera.physicalIds
                    if (children.isEmpty()) {
                        lenses += single(camera)
                    } else {
                        lenses += children.map { child -> fromChild(camera, child) }
                    }
                }

                camera.kind == CameraKind.STANDALONE -> {
                    lenses += single(camera)
                }

                else -> {
                    Unit
                }
            }
        }
        // A listed standalone camera that is also a child of a logical camera was added twice.
        val result = lenses.distinctBy { it.key }.filterNot { it.physicalId == null && it.openId in claimed }
        return result.sortedWith(compareBy({ it.facing.ordinal }, { it.zoomRatio }))
    }

    /** The lens opened first: the main back lens, else the first lens of any kind. */
    fun default(lenses: List<Lens>): Lens? =
        lenses.firstOrNull { it.facing == LensFacing.BACK && it.zoomLabel == "1x" }
            ?: lenses.firstOrNull { it.facing == LensFacing.BACK }
            ?: lenses.firstOrNull()

    private fun single(camera: CameraInfo) =
        Lens(
            key = camera.id,
            openId = camera.id,
            physicalId = null,
            facing = camera.facing,
            zoomLabel = camera.lens.zoomLabel ?: "1x",
            info = camera,
            logical = null,
            zoomRatio = camera.lens.zoomRatioToMain ?: 1f,
        )

    private fun fromChild(
        logical: CameraInfo,
        child: CameraInfo,
    ): Lens {
        val isDefaultLens =
            child.lens.focalLengthsMm.firstOrNull() == logical.lens.focalLengthsMm.firstOrNull() &&
                child.sensor.pixelArrayWidth == logical.sensor.pixelArrayWidth
        return if (isDefaultLens) {
            // The logical camera already shows this lens by default: use it as is, the most compatible path.
            Lens(
                key = "${logical.id}:${child.id}",
                openId = logical.id,
                physicalId = null,
                facing = logical.facing,
                zoomLabel = child.lens.zoomLabel ?: "1x",
                info = logical,
                logical = null,
                zoomRatio = child.lens.zoomRatioToMain ?: 1f,
            )
        } else {
            Lens(
                key = "${logical.id}:${child.id}",
                openId = logical.id,
                physicalId = child.id,
                facing = logical.facing,
                zoomLabel = child.lens.zoomLabel ?: "?",
                info = child,
                logical = logical,
                zoomRatio = child.lens.zoomRatioToMain ?: 1f,
            )
        }
    }
}
