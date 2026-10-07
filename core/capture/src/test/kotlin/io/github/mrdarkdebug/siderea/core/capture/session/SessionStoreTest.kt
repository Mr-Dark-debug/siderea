package io.github.mrdarkdebug.siderea.core.capture.session

import io.github.mrdarkdebug.siderea.core.camera.control.CaptureSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_760_000_000_000L // a fixed instant in October 2025
    private val store by lazy { SessionStore(folder.newFolder("sessions")) { clock } }

    private fun manifest(
        id: String,
        created: Long,
        status: SessionStatus = SessionStatus.RUNNING,
    ) = SessionManifest(
        id = id,
        name = "Night sky",
        kind = SessionKind.TIMELAPSE,
        status = status,
        createdAtEpochMs = created,
        app = AppSnapshot("Siderea", "0.3.0", 300),
        device = DeviceSnapshot("Google", "Pixel 10", "17", 37),
        camera = CameraSnapshot("0:2", "0", null, "1x", "BACK"),
        requested = RequestedCapture(CaptureSettings(), "FOUR_THREE"),
        timelapse = TimelapseConfig(5_000, StopCondition.FRAME_COUNT, frameCount = 100),
    )

    private fun frame(
        i: Int,
        bytes: Long = 4_000_000,
    ) = FrameRecord(
        index = i,
        name = SessionLayout.frameName(i),
        plannedAtMs = i * 5_000L,
        capturedAtEpochMs = clock + i * 5_000L,
        sensorTimestampNs = 1_000_000L * i,
        exposureNs = 4_000_000,
        iso = 200,
        hasJpeg = true,
        jpegBytes = bytes,
        captureMs = 900,
    )

    private fun newSession() = store.create(SessionKind.TIMELAPSE) { id, created -> manifest(id, created) }

    @Test
    fun `deleting JPEG retains RAW and cannot reappear from the journal`() {
        val session = newSession()
        val frame = frame(0).copy(hasDng = true, dngBytes = 20)
        session.appendFrame(frame)
        session.jpegFile(frame.name).writeText("jpeg")
        session.rawFile(frame.name).writeText("raw")
        session.finish(SessionStatus.COMPLETED)
        session.removeMedia(frame.name, raw = false)
        val reopened = store.open(session.id)!!
        assertFalse(reopened.frames.single().hasJpeg)
        assertTrue(reopened.frames.single().hasDng)
        assertTrue(reopened.rawFile(frame.name).isFile)
        assertFalse(reopened.jpegFile(frame.name).exists())
        assertEquals(0L, reopened.frames.single().jpegBytes)
    }

    @Test(expected = IllegalStateException::class)
    fun `running session frames cannot be deleted`() {
        val session = newSession()
        session.appendFrame(frame(0))
        session.removeMedia(SessionLayout.frameName(0), raw = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `frame deletion rejects paths`() {
        val session = newSession()
        session.finish(SessionStatus.COMPLETED)
        session.removeMedia("../session", raw = false)
    }

    @Test
    fun `a new session gets the documented folder layout`() {
        val s = newSession()
        listOf("raw", "jpeg", "previews", "darks", "exports").forEach { assertTrue(File(s.dir, it).isDirectory) }
        assertTrue(File(s.dir, "session.json").isFile)
        assertTrue("folder name should end with the kind: ${s.id}", s.id.endsWith("_Timelapse"))
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}_\d{4}_Timelapse""").matches(s.id))
    }

    @Test
    fun `two sessions in the same minute do not collide`() {
        val a = newSession()
        val b = newSession()
        assertTrue(a.id != b.id)
        assertTrue(b.id.endsWith("_2"))
    }

    @Test
    fun `frame names follow the documented pattern`() {
        assertEquals("IMG_000001", SessionLayout.frameName(0))
        assertEquals("IMG_001234", SessionLayout.frameName(1233))
        assertEquals("DARK_0003", SessionLayout.darkName(2))
        val s = newSession()
        assertTrue(
            s
                .rawFile("IMG_000001")
                .path
                .replace('\\', '/')
                .endsWith("raw/IMG_000001.dng"),
        )
        assertTrue(
            s
                .jpegFile("IMG_000001")
                .path
                .replace('\\', '/')
                .endsWith("jpeg/IMG_000001.jpg"),
        )
        assertTrue(
            s
                .previewFile("IMG_000001")
                .path
                .replace('\\', '/')
                .endsWith("previews/IMG_000001.jpg"),
        )
    }

    @Test
    fun `frames survive a process kill because they are journalled before the manifest`() {
        val s = newSession()
        (0 until 7).forEach { s.appendFrame(frame(it)) }
        // The manifest on disk still says zero frames (it is only rewritten every few frames) ...
        assertTrue(File(s.dir, "session.json").readText().contains("\"frames\": []"))
        // ... but reopening from disk, as after a crash, recovers all seven.
        val reopened = store.open(s.id)!!
        assertEquals(7, reopened.frames.size)
        assertEquals((0 until 7).toList(), reopened.frames.map { it.index })
    }

    @Test
    fun `a half-written last journal line from a crash is ignored`() {
        val s = newSession()
        (0 until 3).forEach { s.appendFrame(frame(it)) }
        File(s.dir, "frames.jsonl").appendText("{\"index\":3,\"name\":\"IMG_0000")
        assertEquals(3, store.open(s.id)!!.frames.size)
    }

    @Test
    fun `a corrupt session folder is skipped by the list rather than breaking it`() {
        newSession()
        val broken = File(folder.root, "sessions/2020-01-01_0000_Astro").apply { mkdirs() }
        File(broken, "session.json").writeText("{ not json")
        assertEquals(1, store.list().size)
        assertNull(store.open(broken.name))
    }

    @Test
    fun `finishing writes the status and the actual per-frame values`() {
        val s = newSession()
        (0 until 12).forEach { s.appendFrame(frame(it)) }
        clock += 99_000
        s.finish(SessionStatus.COMPLETED)
        val text = File(s.dir, "session.json").readText()
        assertTrue(text.contains("\"status\": \"COMPLETED\""))
        assertTrue(text.contains("\"exposureNs\": 4000000"))
        assertTrue(text.contains("\"model\": \"Pixel 10\""))
        val reopened = store.open(s.id)!!
        assertEquals(SessionStatus.COMPLETED, reopened.manifest.status)
        assertNotNull(reopened.manifest.finishedAtEpochMs)
        assertEquals(12, reopened.frames.size)
    }

    @Test
    fun `session json round trips unchanged`() {
        val s = newSession()
        s.appendFrame(frame(0).copy(flags = listOf("moved"), latitude = 51.5, longitude = -0.12))
        s.addEvent("The phone is warm.")
        s.writeManifest()
        val reopened = store.open(s.id)!!
        assertEquals(s.manifest.copy(frames = s.frames), reopened.manifest)
    }

    @Test
    fun `summaries report counts, span and size and sort newest first`() {
        val older = newSession()
        older.appendFrame(frame(0))
        older.appendFrame(frame(1, bytes = 6_000_000))
        clock += 3_600_000
        val newer =
            store.create(
                SessionKind.ASTRO,
            ) { id, created -> manifest(id, created).copy(kind = SessionKind.ASTRO) }
        val list = store.list()
        assertEquals(listOf(newer.id, older.id), list.map { it.id })
        val summary = list.last()
        assertEquals(2, summary.frameCount)
        assertEquals(100, summary.plannedFrames)
        assertEquals(5_000L, summary.spanMs)
        assertEquals(10_000_000L, summary.sizeBytes)
    }

    @Test
    fun `sessions left running are reported as interrupted`() {
        val s = newSession()
        assertEquals(listOf(s.id), store.interrupted(runningId = null).map { it.id })
        assertTrue(
            "the session that is running right now is not interrupted",
            store.interrupted(runningId = s.id).isEmpty(),
        )
        s.finish(SessionStatus.STOPPED)
        assertTrue(store.interrupted(null).isEmpty())
    }

    @Test
    fun `rename changes the name but not the folder, and delete removes everything`() {
        val s = newSession()
        assertTrue(store.rename(s.id, "  Milky Way over the lake  "))
        assertEquals("Milky Way over the lake", store.open(s.id)!!.manifest.name)
        assertEquals(s.id, store.open(s.id)!!.id)
        assertTrue(store.rename(s.id, "   "))
        assertEquals("Milky Way over the lake", store.open(s.id)!!.manifest.name)
        assertTrue(store.delete(s.id))
        assertNull(store.open(s.id))
        assertFalse(store.delete(s.id))
    }

    @Test
    fun `usable frames drop failures, exclusions and frames without files`() {
        val s = newSession()
        s.appendFrame(frame(0))
        s.appendFrame(frame(1).copy(error = "camera busy"))
        s.appendFrame(frame(2))
        s.appendFrame(frame(3).copy(hasJpeg = false))
        s.appendFrame(frame(4))
        assertEquals(listOf(0, 2, 4), s.usableFrames().map { it.index })
        assertEquals(listOf(0, 4), s.usableFrames(excluded = setOf(2)).map { it.index })
    }

    @Test
    fun `appending a frame twice replaces it rather than duplicating it`() {
        val s = newSession()
        s.appendFrame(frame(0))
        s.appendFrame(frame(0).copy(iso = 800))
        assertEquals(1, s.frames.size)
        assertEquals(800, s.frames.single().iso)
        assertEquals(1, store.open(s.id)!!.frames.size)
    }

    @Test
    fun `planned frames come from the stop condition`() {
        assertEquals(100, TimelapseConfig(5_000, StopCondition.FRAME_COUNT, frameCount = 100).plannedFrames)
        assertEquals(13, TimelapseConfig(5_000, StopCondition.DURATION, durationMs = 60_000).plannedFrames)
        assertNull(TimelapseConfig(5_000, StopCondition.UNTIL_STOPPED).plannedFrames)
    }
}
