package io.github.mrdarkdebug.siderea.core.capture.session

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Folder and file names for a session, kept in one place so every module agrees on them. */
object SessionLayout {
    const val MANIFEST = "session.json"
    const val JOURNAL = "frames.jsonl"
    const val RAW_DIR = "raw"
    const val JPEG_DIR = "jpeg"
    const val PREVIEW_DIR = "previews"
    const val DARKS_DIR = "darks"
    const val EXPORTS_DIR = "exports"

    fun frameName(index: Int): String = "IMG_%06d".format(index + 1)

    fun darkName(index: Int): String = "DARK_%04d".format(index + 1)

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").withZone(ZoneId.systemDefault())

    /** `2026-10-04_2310_Astro`. */
    fun folderName(
        createdAtEpochMs: Long,
        kind: SessionKind,
    ): String = stamp.format(Instant.ofEpochMilli(createdAtEpochMs)) + "_" + kind.folderLabel
}

private val sessionJson =
    Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

private val journalJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

/**
 * Where sessions live and how they are listed, created, renamed and deleted. Plain `java.io`, so it is
 * tested on the JVM. Source frames are never deleted automatically: only [delete], on the user's request.
 */
class SessionStore(
    private val root: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    init {
        root.mkdirs()
    }

    /** Creates a session folder and writes its first `session.json`. */
    fun create(
        kind: SessionKind,
        build: (id: String, createdAtEpochMs: Long) -> SessionManifest,
    ): SessionHandle {
        val created = now()
        val base = SessionLayout.folderName(created, kind)
        var dir = File(root, base)
        var suffix = 2
        while (dir.exists()) dir = File(root, "${base}_${suffix++}")
        listOf(
            SessionLayout.RAW_DIR,
            SessionLayout.JPEG_DIR,
            SessionLayout.PREVIEW_DIR,
            SessionLayout.DARKS_DIR,
            SessionLayout.EXPORTS_DIR,
        ).forEach { File(dir, it).mkdirs() }
        val handle = SessionHandle(dir, build(dir.name, created), now)
        handle.writeManifest()
        return handle
    }

    /** Opens a session, merging the journal into the manifest so a crash loses nothing. */
    fun open(id: String): SessionHandle? {
        val dir = File(root, id)
        if (!File(dir, SessionLayout.MANIFEST).isFile) return null
        return runCatching { SessionHandle.load(dir, now) }.getOrNull()
    }

    fun list(): List<SessionSummary> =
        root
            .listFiles { f -> f.isDirectory }
            .orEmpty()
            .mapNotNull { dir -> open(dir.name)?.summary() }
            .sortedByDescending { it.createdAtEpochMs }

    /** Sessions whose status says RUNNING while nothing is running: the process was killed mid-capture. */
    fun interrupted(runningId: String?): List<SessionSummary> =
        list().filter { it.status == SessionStatus.RUNNING && it.id != runningId }

    /** The folder of a session, whether or not it can be opened. */
    fun directory(id: String): File = File(root, id)

    fun delete(id: String): Boolean {
        val dir = File(root, id)
        return dir.isDirectory && dir.deleteRecursively()
    }

    fun rename(
        id: String,
        name: String,
    ): Boolean {
        val handle = open(id) ?: return false
        handle.updateManifest { it.copy(name = name.trim().ifEmpty { it.name }) }
        return true
    }
}

/** One open session: where its files go, and the running record of what has been captured. */
class SessionHandle internal constructor(
    val dir: File,
    manifest: SessionManifest,
    private val now: () -> Long,
) {
    @Volatile
    var manifest: SessionManifest = manifest
        private set

    val id: String get() = dir.name

    private val frameList = ArrayList(manifest.frames)

    @get:Synchronized
    val frames: List<FrameRecord> get() = frameList.toList()

    fun rawFile(name: String) = File(dir, "${SessionLayout.RAW_DIR}/$name.dng")

    fun jpegFile(name: String) = File(dir, "${SessionLayout.JPEG_DIR}/$name.jpg")

    fun previewFile(name: String) = File(dir, "${SessionLayout.PREVIEW_DIR}/$name.jpg")

    fun darkFile(name: String) = File(dir, "${SessionLayout.DARKS_DIR}/$name.dng")

    fun exportsDir() = File(dir, SessionLayout.EXPORTS_DIR)

    /**
     * Records a frame. It goes to the journal first (one fsync-able line), which is what makes a killed
     * process lose at most the frame that was in flight. The manifest is rewritten every few frames.
     */
    @Synchronized
    fun appendFrame(record: FrameRecord) {
        File(dir, SessionLayout.JOURNAL).appendText(journalJson.encodeToString(FrameRecord.serializer(), record) + "\n")
        val position = frameList.indexOfFirst { it.index == record.index }
        if (position >= 0) frameList[position] = record else frameList.add(record)
        if (frameList.size % MANIFEST_EVERY == 0) writeManifest()
    }

    @Synchronized
    fun addEvent(text: String) {
        manifest = manifest.copy(events = manifest.events + SessionEvent(now(), text))
    }

    @Synchronized
    fun updateManifest(change: (SessionManifest) -> SessionManifest) {
        manifest = change(manifest)
        writeManifest()
    }

    /** Ends the session with [status], flushing everything to `session.json`. */
    @Synchronized
    fun finish(status: SessionStatus) {
        manifest = manifest.copy(status = status, finishedAtEpochMs = now())
        writeManifest()
    }

    /** Atomically replaces `session.json`: a reader sees the old file or the new one, never half of one. */
    @Synchronized
    fun writeManifest() {
        manifest = manifest.copy(frames = frameList.toList())
        val target = File(dir, SessionLayout.MANIFEST)
        val temp = File(dir, SessionLayout.MANIFEST + ".tmp")
        temp.writeText(sessionJson.encodeToString(SessionManifest.serializer(), manifest))
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: IOException) {
            // Some filesystems can't do an atomic move; a plain replace is the next best thing.
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun summary(): SessionSummary {
        val frames = frames
        val span = if (frames.size >= 2) frames.last().capturedAtEpochMs - frames.first().capturedAtEpochMs else 0L
        return SessionSummary(
            id = id,
            name = manifest.name,
            kind = manifest.kind,
            status = manifest.status,
            createdAtEpochMs = manifest.createdAtEpochMs,
            frameCount = frames.size,
            plannedFrames = manifest.timelapse?.plannedFrames,
            spanMs = span,
            sizeBytes = frames.sumOf { it.jpegBytes + it.dngBytes },
            folderName = dir.name,
        )
    }

    /** Frames that should feed export and stacking: errors and user-excluded frames removed. */
    fun usableFrames(excluded: Set<Int> = emptySet()): List<FrameRecord> =
        frames.filter { it.error == null && it.index !in excluded && (it.hasJpeg || it.hasDng) }

    companion object {
        private const val MANIFEST_EVERY = 10

        internal fun load(
            dir: File,
            now: () -> Long,
        ): SessionHandle {
            val manifest =
                sessionJson.decodeFromString(
                    SessionManifest.serializer(),
                    File(dir, SessionLayout.MANIFEST).readText(),
                )
            val merged =
                (manifest.frames + readJournal(dir))
                    .associateBy { it.index }
                    .toSortedMap()
                    .values
                    .toList()
            return SessionHandle(dir, manifest.copy(frames = merged), now)
        }

        /** Journal lines in order; a half-written last line from a crash is skipped, not fatal. */
        internal fun readJournal(dir: File): List<FrameRecord> {
            val file = File(dir, SessionLayout.JOURNAL)
            if (!file.isFile) return emptyList()
            return file.readLines().mapNotNull { line ->
                if (line.isBlank()) {
                    null
                } else {
                    runCatching { journalJson.decodeFromString(FrameRecord.serializer(), line) }.getOrNull()
                }
            }
        }
    }
}
