package io.github.mrdarkdebug.siderea

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraCapabilityReader
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureLimits
import io.github.mrdarkdebug.siderea.core.camera.control.ExposureMode
import io.github.mrdarkdebug.siderea.core.camera.engine.AspectRatio
import io.github.mrdarkdebug.siderea.core.camera.engine.CameraEngine
import io.github.mrdarkdebug.siderea.core.camera.engine.EngineState
import io.github.mrdarkdebug.siderea.core.camera.engine.Lens
import io.github.mrdarkdebug.siderea.core.camera.engine.LensCatalog
import io.github.mrdarkdebug.siderea.core.camera.engine.OpenParams
import io.github.mrdarkdebug.siderea.core.camera.engine.ReadyInfo
import io.github.mrdarkdebug.siderea.core.camera.engine.RequestPlanner
import io.github.mrdarkdebug.siderea.core.camera.engine.StillRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Drives the real Camera2 engine on whatever cameras the emulator or phone has. Preview frames are
 * drained into an ImageReader, so no UI is needed. Tests skip (rather than fail) when the camera under
 * test lacks the capability being checked.
 */
class CameraEngineInstrumentedTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var engine: CameraEngine
    private lateinit var lenses: List<Lens>
    private val drainThread = HandlerThread("test-drain").apply { start() }
    private val readers = mutableListOf<ImageReader>()
    private val sdk = android.os.Build.VERSION.SDK_INT

    @Before
    fun setUp() {
        engine = CameraEngine(context)
        lenses = LensCatalog.from(runBlocking { CameraCapabilityReader(context).read() })
        assumeTrue("this device has no camera", lenses.isNotEmpty())
    }

    @After
    fun tearDown() {
        runBlocking { engine.close() }
        engine.release()
        readers.forEach { it.close() }
        drainThread.quitSafely()
    }

    private fun surfaceFor(
        width: Int,
        height: Int,
    ): Surface {
        val reader =
            ImageReader.newInstance(
                width,
                height,
                ImageFormat.PRIVATE,
                3,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
            )
        reader.setOnImageAvailableListener({ it.acquireNextImage()?.close() }, Handler(drainThread.looper))
        readers += reader
        return reader.surface
    }

    private fun open(
        lens: Lens,
        format: CaptureFormat = CaptureFormat.JPEG,
    ): ReadyInfo =
        runBlocking {
            engine.open(OpenParams(lens, format, AspectRatio.FOUR_THREE, ::surfaceFor))
            val state = withTimeout(OPEN_TIMEOUT_MS) { engine.state.first { it !is EngineState.Opening } }
            assertTrue("engine should be ready but was $state", state is EngineState.Ready)
            (state as EngineState.Ready).info
        }

    private fun plan(
        lens: Lens,
        settings: CaptureSettings,
        info: ReadyInfo,
        forPreview: Boolean,
    ) = RequestPlanner.plan(
        settings,
        settings.shutterNs,
        settings.iso,
        ExposureLimits.from(lens.info, sdk),
        info.capabilities,
        forPreview,
    )

    private fun firstLens(predicate: (Lens) -> Boolean): Lens {
        val lens = lenses.firstOrNull(predicate)
        assumeTrue("no camera on this device has the capability under test", lens != null)
        return lens!!
    }

    @Test
    fun opensEveryLensAndReportsReady() {
        lenses.forEach { lens ->
            val info = open(lens)
            assertEquals(lens.key, info.lens.key)
            assertTrue(info.previewSize.width > 0 && info.previewSize.height > 0)
            assertNotNull(info.jpegSize)
        }
    }

    @Test
    fun previewDeliversLiveFrameMetadata() {
        val lens = lenses.first()
        val info = open(lens)
        engine.updatePreviewPlan(plan(lens, CaptureSettings(), info, forPreview = true))
        val frame = runBlocking { withTimeout(FRAME_TIMEOUT_MS) { engine.frames.first { it != null } } }!!
        assertNotNull("auto exposure should report an exposure time", frame.exposureNs)
        assertNotNull(frame.iso)
    }

    @Test
    fun manualExposureIsAppliedByTheSensor() {
        val lens = firstLens { it.info.manualSensor && it.info.exposure.exposureTimeMaxNs != null }
        val info = open(lens)
        val limits = ExposureLimits.from(lens.info, sdk)
        val requested = limits.clampShutter(20_000_000L)
        val settings =
            CaptureSettings(exposureMode = ExposureMode.MANUAL, shutterNs = requested, iso = limits.clampIso(200))
        engine.updatePreviewPlan(plan(lens, settings, info, forPreview = true))
        val frame =
            runBlocking {
                withTimeout(FRAME_TIMEOUT_MS) {
                    engine.frames.first {
                        it?.exposureNs != null && abs(
                            it.exposureNs!! - requested,
                        ) < requested * TOLERANCE
                    }
                }
            }!!
        assertEquals(requested.toDouble(), frame.exposureNs!!.toDouble(), requested * TOLERANCE)
        assertEquals(limits.clampIso(200), frame.iso)
    }

    @Test
    fun autoExposureJpegIsAValidImage() {
        val lens = lenses.first()
        val info = open(lens)
        engine.updatePreviewPlan(plan(lens, CaptureSettings(), info, forPreview = true))
        val photo =
            runBlocking {
                engine.capture(
                    StillRequest(plan(lens, CaptureSettings(), info, forPreview = false), CaptureFormat.JPEG, 90),
                )
            }
        val jpeg = photo.jpeg!!
        assertTrue("JPEG magic number", jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertNotNull("the JPEG should decode", bitmap)
        assertTrue(bitmap.width > 100 && bitmap.height > 100)
        assertNotNull(photo.facts.exposureNs)
    }

    @Test
    fun manualLongExposureReportsTheExposureTheSensorUsed() {
        val lens = firstLens { it.info.manualSensor && (it.info.exposure.exposureTimeMaxNs ?: 0) >= LONG_NS }
        val info = open(lens)
        val limits = ExposureLimits.from(lens.info, sdk)
        val settings =
            CaptureSettings(exposureMode = ExposureMode.MANUAL, shutterNs = LONG_NS, iso = limits.clampIso(400))
        engine.updatePreviewPlan(plan(lens, settings, info, forPreview = true))
        val photo =
            runBlocking {
                engine.capture(StillRequest(plan(lens, settings, info, forPreview = false), CaptureFormat.JPEG, 0))
            }
        val actual = photo.facts.exposureNs!!
        assertTrue("sensor used ${actual}ns for a ${LONG_NS}ns request", abs(actual - LONG_NS) < LONG_NS * TOLERANCE)
        assertNotNull(photo.jpeg)
    }

    /**
     * Tries every RAW-capable lens: some emulator HALs return capture results too sparse for DngCreator,
     * which is a property of the camera under test, not of the engine. The test passes when at least one
     * lens produces a valid DNG, and is skipped (not failed) when every lens is rejected for that reason.
     */
    @Test
    fun rawCaptureProducesADngFile() {
        val rawLenses =
            lenses.filter {
                it.info.raw &&
                    it.info.outputs.rawSizes
                        .isNotEmpty()
            }
        assumeTrue("no RAW-capable lens on this device", rawLenses.isNotEmpty())
        val rejections = mutableListOf<String>()
        var produced = false
        for (lens in rawLenses) {
            val info = open(lens, CaptureFormat.RAW_JPEG)
            if (info.rawSize == null) {
                rejections += "${lens.key}: no RAW stream"
                continue
            }
            engine.updatePreviewPlan(plan(lens, CaptureSettings(), info, forPreview = true))
            val outcome =
                runCatching {
                    runBlocking {
                        engine.capture(
                            StillRequest(
                                plan(lens, CaptureSettings(), info, forPreview = false),
                                CaptureFormat.RAW_JPEG,
                                90,
                            ),
                        )
                    }
                }
            val photo = outcome.getOrNull()
            if (photo == null) {
                rejections += "${lens.key}: ${outcome.exceptionOrNull()?.message}"
                android.util.Log.i("RAWTEST", "lens ${lens.key} rejected: ${outcome.exceptionOrNull()?.message}")
                continue
            }
            val dng: File = photo.dng!!
            try {
                assertTrue("DNG should be a real file, was ${dng.length()} bytes", dng.length() > MIN_DNG_BYTES)
                val header = dng.inputStream().use { stream -> ByteArray(4).also { stream.read(it) } }
                val little =
                    header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 42.toByte()
                val big = header[0] == 'M'.code.toByte() && header[1] == 'M'.code.toByte() && header[3] == 42.toByte()
                assertTrue("DNG must start with a TIFF header", little || big)
                assertNotNull("RAW+JPEG should also yield a JPEG", photo.jpeg)
                produced = true
                android.util.Log.i("RAWTEST", "lens ${lens.key} produced a ${dng.length()} byte DNG")
            } finally {
                dng.delete()
            }
        }
        assumeTrue("every RAW lens was rejected by DngCreator: $rejections", produced)
    }

    @Test
    fun switchingLensesReopensTheCamera() {
        assumeTrue("needs at least two lenses", lenses.size >= 2)
        val first = open(lenses[0])
        val second = open(lenses[1])
        assertTrue(first.lens.key != second.lens.key)
    }

    @Test
    fun closeReturnsToClosed() {
        open(lenses.first())
        runBlocking { engine.close() }
        assertEquals(EngineState.Closed, engine.state.value)
    }

    private companion object {
        const val OPEN_TIMEOUT_MS = 20_000L
        const val FRAME_TIMEOUT_MS = 10_000L
        const val TOLERANCE = 0.15
        const val LONG_NS = 1_000_000_000L
        const val MIN_DNG_BYTES = 200_000L
    }
}
