package io.github.mrdarkdebug.siderea

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import io.github.mrdarkdebug.siderea.capture.LaunchRequest
import io.github.mrdarkdebug.siderea.capture.TimelapseService
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraCapabilityReader
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureFormat
import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import io.github.mrdarkdebug.siderea.core.camera.engine.LensCatalog
import io.github.mrdarkdebug.siderea.core.capture.session.SessionManifest
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.session.StopCondition
import io.github.mrdarkdebug.siderea.core.capture.session.TimelapseConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Runs the real foreground service against the emulator's camera and checks what lands on disk: the
 * documented folder layout, the actual per-frame values in `session.json`, clean stop and clean finish.
 */
class TimelapseServiceInstrumentedTest {
    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.CAMERA,
            *(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()),
        )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sessionsRoot = File(context.getExternalFilesDir(null), "Siderea")
    private val json = Json { ignoreUnknownKeys = true }
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        sessionsRoot.deleteRecursively()
        // The activity must be on screen: Android only lets a camera foreground service start from the foreground.
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(SETTLE_MS)
    }

    @After
    fun tearDown() {
        TimelapseService.stop(context)
        Thread.sleep(SETTLE_MS)
        scenario?.close()
        sessionsRoot.deleteRecursively()
    }

    private fun request(config: TimelapseConfig): LaunchRequest {
        val report = runBlocking { CameraCapabilityReader(context).read() }
        val lens = LensCatalog.default(LensCatalog.from(report))
        assumeTrue("no camera on this device", lens != null)
        return LaunchRequest(
            lensKey = lens!!.key,
            settings = CaptureSettings(format = CaptureFormat.JPEG),
            aspect = "FOUR_THREE",
            config = config,
            lockedShutterNs = 8_333_333L,
            lockedIso = 100,
            jpegOrientation = 0,
            keepScreenOn = false,
            name = "Instrumented test",
        )
    }

    private fun latestManifest(): SessionManifest? {
        val dir = sessionsRoot.listFiles { f -> f.isDirectory }?.maxByOrNull { it.lastModified() } ?: return null
        val file = File(dir, "session.json")
        return if (file.isFile) json.decodeFromString(SessionManifest.serializer(), file.readText()) else null
    }

    private fun latestDir(): File = sessionsRoot.listFiles { f -> f.isDirectory }!!.maxByOrNull { it.lastModified() }!!

    private fun waitFor(
        timeoutMs: Long,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MS)
        }
        return condition()
    }

    @Test
    fun aShortSessionCompletesAndLeavesTheDocumentedFiles() {
        val config = TimelapseConfig(intervalMs = 2_500, stop = StopCondition.FRAME_COUNT, frameCount = 3)
        TimelapseService.start(context, request(config))
        assertTrue(
            "the session should complete",
            waitFor(60_000) { latestManifest()?.status == SessionStatus.COMPLETED },
        )
        val manifest = latestManifest()!!
        assertEquals(3, manifest.frames.count { it.error == null })
        assertEquals("Instrumented test", manifest.name)
        assertNotNull(manifest.finishedAtEpochMs)
        // Actual values from the capture result, not the request.
        manifest.frames.forEach { frame ->
            assertTrue("frame ${frame.index} should record its sensor exposure", frame.exposureNs != null)
            assertTrue(frame.hasJpeg && frame.jpegBytes > 1_000)
            assertTrue("frame took time", frame.captureMs > 0)
        }
        val dir = latestDir()
        listOf("raw", "jpeg", "previews", "darks", "exports").forEach { assertTrue(File(dir, it).isDirectory) }
        assertEquals(3, File(dir, "jpeg").listFiles()!!.size)
        assertEquals(3, File(dir, "previews").listFiles()!!.size)
        assertTrue(File(dir, "jpeg/IMG_000001.jpg").length() > 1_000)
        // Frames are spaced by the interval, from the schedule, not by how long captures took.
        val gaps = manifest.frames.zipWithNext { a, b -> b.plannedAtMs - a.plannedAtMs }
        gaps.forEach { assertEquals(2_500L, it) }
        assertEquals(manifest.device.model, Build.MODEL)
    }

    @Test
    fun stoppingMidSessionKeepsEveryFrameAndMarksItStopped() {
        val config = TimelapseConfig(intervalMs = 2_500, stop = StopCondition.UNTIL_STOPPED)
        TimelapseService.start(context, request(config))
        assertTrue(
            "two frames should arrive",
            waitFor(45_000) {
                (latestManifest()?.frames?.size ?: 0) >= 2 ||
                    latestJournalFrames() >= 2
            },
        )
        TimelapseService.stop(context)
        assertTrue(
            "the session should end as stopped",
            waitFor(30_000) { latestManifest()?.status == SessionStatus.STOPPED },
        )
        val manifest = latestManifest()!!
        assertTrue(manifest.frames.size >= 2)
        manifest.frames.filter { it.error == null }.forEach {
            assertTrue(
                File(latestDir(), "jpeg/${it.name}.jpg").isFile,
            )
        }
    }

    private fun latestJournalFrames(): Int =
        runCatching { File(latestDir(), "frames.jsonl").readLines().count { it.isNotBlank() } }.getOrDefault(0)

    private companion object {
        const val SETTLE_MS = 2_000L
        const val POLL_MS = 500L
    }
}
