package io.github.mrdarkdebug.siderea

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraCapabilityReader
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraKind
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityJson
import io.github.mrdarkdebug.siderea.core.camera.capability.SupportStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real reader against whatever cameras this device or emulator has. The assertions are
 * about internal consistency, because no particular phone is assumed.
 */
class CameraCapabilityReaderInstrumentedTest {
    private val report =
        runBlocking {
            CameraCapabilityReader(ApplicationProvider.getApplicationContext<Context>()).read()
        }

    @Test
    fun readsDeviceIdentityFromTheBuild() {
        assertEquals(Build.MODEL, report.device.model)
        assertEquals(Build.VERSION.SDK_INT, report.device.sdkInt)
        assertEquals("Siderea", report.app.name)
    }

    @Test
    fun findsAtLeastOneCameraAndReadsIt() {
        assertTrue("expected at least one camera, errors=${report.readErrors}", report.cameras.isNotEmpty())
        assertTrue(report.cameras.any { it.error == null })
    }

    @Test
    fun cameraIdsAreUnique() {
        val ids = report.cameras.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyListedIdAppearsInTheReport() {
        val reported = report.cameras.map { it.id }.toSet()
        assertTrue(reported.containsAll(report.cameraIdsListed))
    }

    @Test
    fun physicalCamerasPointBackToALogicalParent() {
        val byId = report.cameras.associateBy { it.id }
        report.cameras.filter { it.kind == CameraKind.PHYSICAL }.forEach { physical ->
            val parent = byId[physical.parentLogicalId]
            assertNotNull("physical camera ${physical.id} has no parent in the report", parent)
            assertTrue(physical.id in parent!!.physicalIds)
        }
    }

    @Test
    fun readableCamerasReportRangesConsistentWithTheirCapabilities() {
        report.cameras.filter { it.error == null }.forEach { camera ->
            if (camera.manualSensor) {
                assertNotNull(
                    "camera ${camera.id}: MANUAL_SENSOR but no exposure range",
                    camera.exposure.exposureTimeMaxNs,
                )
                assertNotNull("camera ${camera.id}: MANUAL_SENSOR but no ISO range", camera.exposure.isoMax)
            }
            val min = camera.exposure.exposureTimeMinNs
            val max = camera.exposure.exposureTimeMaxNs
            if (min != null && max != null) assertTrue("camera ${camera.id}: shutter range inverted", min <= max)
            if (camera.raw) assertTrue("camera ${camera.id}: RAW claimed", "RAW" in camera.capabilities)
            assertEquals(8, camera.features.size)
        }
    }

    @Test
    fun zoomLabelsAreAssignedAndTheMainLensIsOneX() {
        val labelled = report.cameras.filter { it.lens.zoomLabel != null }
        assertTrue(labelled.isNotEmpty())
        report.cameras.groupBy { it.facing }.forEach { (facing, group) ->
            if (group.any { it.lens.zoomLabel != null }) {
                assertTrue("no 1x lens facing $facing", group.any { it.lens.zoomLabel == "1x" })
            }
        }
    }

    @Test
    fun android16KeysAreOnlyReportedOnAndroid16() {
        report.cameras.filter { it.error == null }.forEach { camera ->
            assertEquals(Build.VERSION.SDK_INT >= 36, camera.android16.platformHasApi36)
            if (Build.VERSION.SDK_INT < 36) {
                assertFalse(camera.android16.cctSupported)
                assertFalse(camera.android16.hybridAeSupported)
            }
        }
    }

    @Test
    fun verdictsNeverClaimWhatTheHardwareDoesNotReport() {
        report.cameras.filter { it.error == null && !it.manualSensor }.forEach { camera ->
            val manual = camera.features.first { it.id == "manual_exposure" }
            assertEquals(SupportStatus.UNSUPPORTED, manual.status)
        }
    }

    @Test
    fun reportSurvivesJsonRoundTripOnDevice() {
        assertEquals(report, CapabilityJson.decode(CapabilityJson.encode(report)))
    }
}
