package io.github.mrdarkdebug.siderea.core.camera.capability

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/** Something that can produce a [CapabilityReport]. Lets tests and previews swap in fakes. */
fun interface CapabilitySource {
    suspend fun read(): CapabilityReport
}

/**
 * Enumerates every camera Camera2 knows about, including the physical sub-cameras behind logical
 * ones, and records what each one reports. Needs no runtime permission: characteristics are public.
 */
class CameraCapabilityReader(
    private val context: Context,
) : CapabilitySource {
    override suspend fun read(): CapabilityReport = withContext(Dispatchers.Default) { readBlocking() }

    internal fun readBlocking(): CapabilityReport {
        val errors = mutableListOf<String>()
        val manager = context.getSystemService(CameraManager::class.java)
        val listedIds = listIds(manager, errors)

        val cameras = LinkedHashMap<String, CameraInfo>()
        val characteristics = LinkedHashMap<String, CameraCharacteristics>()

        // Pass 1: every camera Camera2 lists.
        for (id in listedIds) {
            val chars = characteristicsOf(manager, id) { errors += "Camera $id: $it" }
            if (chars == null) {
                cameras[id] = failed(id, CameraKind.STANDALONE, null, "Could not read this camera.")
                continue
            }
            characteristics[id] = chars
            val physical = chars.physicalCameraIds.sorted()
            val kind = if (physical.isEmpty()) CameraKind.STANDALONE else CameraKind.LOGICAL
            cameras[id] = describe(manager, id, chars, kind, null, physical, errors)
        }

        // Pass 2: physical sub-cameras that are only reachable through a logical camera.
        for ((logicalId, logicalChars) in characteristics.toMap()) {
            for (physicalId in logicalChars.physicalCameraIds.sorted()) {
                val existing = cameras[physicalId]
                if (existing != null) {
                    if (existing.parentLogicalId == null) {
                        cameras[physicalId] = existing.copy(parentLogicalId = logicalId)
                    }
                    continue
                }
                val chars =
                    characteristicsOf(manager, physicalId) {
                        errors += "Physical camera $physicalId (behind $logicalId): $it"
                    }
                cameras[physicalId] =
                    if (chars == null) {
                        failed(
                            physicalId,
                            CameraKind.PHYSICAL,
                            logicalId,
                            "This physical camera can't be queried directly on this phone.",
                        )
                    } else {
                        describe(manager, physicalId, chars, CameraKind.PHYSICAL, logicalId, emptyList(), errors)
                    }
            }
        }

        return CapabilityReport(
            generatedAt = Instant.now().toString(),
            app = appInfo(),
            device = deviceInfo(),
            cameraIdsListed = listedIds,
            concurrentCameraSets = concurrentSets(manager, errors),
            cameras = withZoomLabels(cameras.values.toList()),
            readErrors = errors,
        )
    }

    private fun describe(
        manager: CameraManager,
        id: String,
        chars: CameraCharacteristics,
        kind: CameraKind,
        parentLogicalId: String?,
        physicalIds: List<String>,
        errors: MutableList<String>,
    ): CameraInfo =
        try {
            CharacteristicsMapper.map(
                id,
                chars,
                kind,
                parentLogicalId,
                physicalIds,
                extensions(manager, id, kind, errors),
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            // A vendor HAL returning something unexpected must not take the whole report down.
            errors += "Camera $id: unexpected error while reading (${e.javaClass.simpleName}: ${e.message})"
            failed(id, kind, parentLogicalId, "Reading this camera failed: ${e.message ?: e.javaClass.simpleName}")
        }

    private fun failed(
        id: String,
        kind: CameraKind,
        parent: String?,
        message: String,
    ) = CameraInfo(id = id, kind = kind, parentLogicalId = parent, error = message)

    private fun listIds(
        manager: CameraManager,
        errors: MutableList<String>,
    ): List<String> =
        try {
            manager.cameraIdList.toList()
        } catch (e: CameraAccessException) {
            errors += "The camera service refused to list cameras (${e.message}). Is another app using the camera?"
            emptyList()
        }

    private fun characteristicsOf(
        manager: CameraManager,
        id: String,
        onError: (String) -> Unit,
    ): CameraCharacteristics? =
        try {
            manager.getCameraCharacteristics(id)
        } catch (e: CameraAccessException) {
            onError("camera access error (${e.message})")
            null
        } catch (e: IllegalArgumentException) {
            onError("not available (${e.message})")
            null
        }

    private fun extensions(
        manager: CameraManager,
        id: String,
        kind: CameraKind,
        errors: MutableList<String>,
    ): List<String> {
        // Extensions are only offered for cameras Camera2 lists, and only from Android 12.
        if (kind == CameraKind.PHYSICAL || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return try {
            manager
                .getCameraExtensionCharacteristics(id)
                .supportedExtensions
                .map(EnumNames::extension)
                .sorted()
        } catch (e: CameraAccessException) {
            errors += "Camera $id: extensions unavailable (${e.message})."
            emptyList()
        } catch (e: IllegalArgumentException) {
            errors += "Camera $id: extensions unavailable (${e.message})."
            emptyList()
        }
    }

    private fun concurrentSets(
        manager: CameraManager,
        errors: MutableList<String>,
    ): List<List<String>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return try {
            manager.concurrentCameraIds.map { it.sorted() }.sortedBy { it.joinToString(",") }
        } catch (e: CameraAccessException) {
            errors += "Concurrent camera sets unavailable (${e.message})."
            emptyList()
        }
    }

    /**
     * Labels every camera 0.5x / 1x / 5x relative to the main lens facing the same way. The main lens
     * is chosen from physical focal lengths, not hardcoded ids.
     */
    private fun withZoomLabels(cameras: List<CameraInfo>): List<CameraInfo> {
        val mainByFacing: Map<LensFacing, Float> =
            cameras
                .filter { it.error == null && it.lens.equivalentFocalLengthsMm.isNotEmpty() }
                .groupBy { it.facing }
                .mapNotNull { (facing, group) ->
                    val mainId = ZoomLabels.pickMain(group.map { it.id to it.lens.equivalentFocalLengthsMm.first() })
                    group.firstOrNull { it.id == mainId }?.let { facing to it.lens.equivalentFocalLengthsMm.first() }
                }.toMap()
        return cameras.map { camera ->
            val reference = mainByFacing[camera.facing]
            val equivalent = camera.lens.equivalentFocalLengthsMm.firstOrNull()
            if (reference == null || equivalent == null) {
                camera
            } else {
                val ratio = equivalent / reference
                camera.copy(lens = camera.lens.copy(zoomRatioToMain = ratio, zoomLabel = ZoomLabels.format(ratio)))
            }
        }
    }

    private fun appInfo(): AppInfo {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        return AppInfo(
            name = "Siderea",
            versionName = info?.versionName ?: "unknown",
            versionCode = info?.longVersionCode ?: 0L,
        )
    }

    private fun deviceInfo() =
        DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            hardware = Build.HARDWARE,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            fingerprint = Build.FINGERPRINT,
        )
}
