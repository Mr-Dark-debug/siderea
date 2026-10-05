package io.github.mrdarkdebug.siderea

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import io.github.mrdarkdebug.siderea.core.export.BitmapTiff
import io.github.mrdarkdebug.siderea.core.export.CropAspect
import io.github.mrdarkdebug.siderea.core.export.Deflicker
import io.github.mrdarkdebug.siderea.core.export.DeflickerLevel
import io.github.mrdarkdebug.siderea.core.export.ExportException
import io.github.mrdarkdebug.siderea.core.export.MediaStorePublisher
import io.github.mrdarkdebug.siderea.core.export.OutputSize
import io.github.mrdarkdebug.siderea.core.export.PublishKind
import io.github.mrdarkdebug.siderea.core.export.TimelapseVideoExporter
import io.github.mrdarkdebug.siderea.core.export.VideoCodec
import io.github.mrdarkdebug.siderea.core.export.VideoEncoders
import io.github.mrdarkdebug.siderea.core.export.VideoSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Encodes real MP4 files with the phone's own encoder and reads them back, rather than trusting the API. */
class VideoExportInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var work: File

    @Before
    fun setUp() {
        work = File(context.cacheDir, "export-test").also { it.deleteRecursively() }
        work.mkdirs()
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    /** A frame with a moving block and the given overall brightness (0..255). */
    private fun frame(
        index: Int,
        brightness: Int,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(brightness, brightness, brightness))
        val paint = Paint().apply { color = Color.rgb(255 - brightness, 40, 40) }
        canvas.drawRect(index * 20f, 100f, index * 20f + 80f, 200f, paint)
        val file = File(work, "IMG_%06d.jpg".format(index + 1))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return file
    }

    private fun frames(
        count: Int,
        brightness: (Int) -> Int = { 120 },
    ) = List(count) { frame(it, brightness(it)) }

    private class VideoInfo(
        val mime: String,
        val width: Int,
        val height: Int,
        val samples: Int,
        val durationUs: Long,
    )

    private fun inspect(file: File): VideoInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track =
                (0 until extractor.trackCount).first {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")
                }
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            var samples = 0
            while (extractor.sampleTime >= 0) {
                samples++
                extractor.advance()
            }
            return VideoInfo(
                format.getString(MediaFormat.KEY_MIME)!!,
                format.getInteger(MediaFormat.KEY_WIDTH),
                format.getInteger(MediaFormat.KEY_HEIGHT),
                samples,
                format.getLong(MediaFormat.KEY_DURATION),
            )
        } finally {
            extractor.release()
        }
    }

    private fun lumaAt(
        file: File,
        timeUs: Long,
    ): Double {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val bitmap = checkNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return Deflicker.meanLuma(pixels)
        } finally {
            retriever.release()
        }
    }

    private fun export(
        list: List<File>,
        spec: VideoSpec,
        name: String = "out.mp4",
    ) = runBlocking { TimelapseVideoExporter().export(list, spec, File(work, name)) }

    @Test
    fun h264VideoHasOneSampleForEveryFrameAtTheRightSize() {
        assumeTrue(VideoCodec.H264 in VideoEncoders.available())
        val result = export(frames(12), VideoSpec(fps = 12, size = OutputSize.SOURCE))
        val info = inspect(result.file)
        assertEquals("video/avc", info.mime)
        assertEquals(12, result.frames)
        assertEquals(12, info.samples)
        assertEquals(WIDTH, info.width)
        assertEquals(HEIGHT, info.height)
        // 12 frames at 12 fps is one second, give or take one frame.
        assertTrue("duration ${info.durationUs}", abs(info.durationUs - 1_000_000L) < 150_000L)
        assertFalse(result.shrunk)
    }

    @Test
    fun theTimelineIsEvenlySpacedWhateverTheWallClockDid() {
        assumeTrue(VideoCodec.H264 in VideoEncoders.available())
        val result = export(frames(10), VideoSpec(fps = 10, size = OutputSize.SOURCE))
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(result.file.absolutePath)
            extractor.selectTrack(0)
            val times = ArrayList<Long>()
            while (extractor.sampleTime >= 0) {
                times += extractor.sampleTime
                extractor.advance()
            }
            times.sort()
            times.zipWithNext { a, b -> assertEquals(100_000.0, (b - a).toDouble(), 2_000.0) }
        } finally {
            extractor.release()
        }
    }

    @Test
    fun cropAndScaleChangeTheOutputSize() {
        assumeTrue(VideoCodec.H264 in VideoEncoders.available())
        val result = export(frames(6), VideoSpec(fps = 6, size = OutputSize.HD, crop = CropAspect.SQUARE))
        val info = inspect(result.file)
        // A square cut of a 640x480 frame is 480x480, and 720p never enlarges it.
        assertEquals(HEIGHT, info.width)
        assertEquals(HEIGHT, info.height)
    }

    @Test
    fun hevcVideoWorksWhereThePhoneHasAnEncoder() {
        assumeTrue("no HEVC encoder here", VideoCodec.HEVC in VideoEncoders.available())
        val result = export(frames(8), VideoSpec(codec = VideoCodec.HEVC, fps = 8, size = OutputSize.SOURCE))
        val info = inspect(result.file)
        assertEquals("video/hevc", info.mime)
        assertEquals(8, info.samples)
    }

    @Test
    fun deflickerReducesBrightnessSwings() {
        assumeTrue(VideoCodec.H264 in VideoEncoders.available())
        val flicker = frames(16) { if (it % 2 == 0) 90 else 150 }
        val plain = export(flicker, VideoSpec(fps = 8, size = OutputSize.SOURCE), "plain.mp4")
        val fixed =
            export(
                flicker,
                VideoSpec(fps = 8, size = OutputSize.SOURCE, deflicker = DeflickerLevel.STRONG),
                "fixed.mp4",
            )

        fun swing(file: File): Double {
            val values = (2 until 14).map { lumaAt(file, it * 125_000L) }
            return values.max() - values.min()
        }
        val before = swing(plain.file)
        val after = swing(fixed.file)
        assertTrue("brightness swing should shrink: $before -> $after", after < before * 0.6)
    }

    @Test
    fun anUnreadableFrameIsSkippedNotFatal() {
        assumeTrue(VideoCodec.H264 in VideoEncoders.available())
        val list = frames(8).toMutableList()
        list[3].writeBytes(ByteArray(40) { 7 })
        val result = export(list, VideoSpec(fps = 8, size = OutputSize.SOURCE))
        assertEquals(1, result.skipped)
        assertEquals(7, inspect(result.file).samples)
    }

    @Test
    fun noReadableFramesGivesAClearErrorAndNoFile() {
        val bad = File(work, "IMG_000001.jpg").also { it.writeBytes(ByteArray(20)) }
        val out = File(work, "never.mp4")
        val error =
            assertThrows(ExportException::class.java) {
                runBlocking { TimelapseVideoExporter().export(listOf(bad), VideoSpec(), out) }
            }
        assertTrue(error.message!!.isNotBlank())
        assertFalse(out.exists())
    }

    @Test
    fun cancellingStopsTheEncoderAndRemovesThePartialFile() {
        assumeTrue(VideoCodec.H264 in VideoEncoders.available())
        val list = frames(40)
        val out = File(work, "cancelled.mp4")
        runBlocking {
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val job: Job =
                async {
                    TimelapseVideoExporter().export(list, VideoSpec(fps = 10, size = OutputSize.SOURCE), out) {
                        if (it.done >= 3) started.complete(Unit)
                    }
                }
            started.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }
        assertFalse("partial file must be deleted", out.exists())
    }

    @Test
    fun tiffFromARealJpegHasTheRightSizeAndHeader() {
        val jpeg = frame(0, 100)
        val bitmap =
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(jpeg)) { d, _, _ ->
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        val tiff = File(work, "frame.tif")
        BitmapTiff.write(bitmap, tiff)
        val bytes = tiff.readBytes()
        assertEquals(0x49, bytes[0].toInt())
        assertEquals(42, bytes[2].toInt())
        assertTrue(bytes.size > WIDTH * HEIGHT * 3)
        assertTrue(bytes.size < WIDTH * HEIGHT * 3 + 4_096)
    }

    @Test
    fun publishedFilesAppearInMediaStoreAndCanBeRemoved() {
        val zip = File(work, "p.zip").also { it.writeBytes(ByteArray(2_000) { 1 }) }
        val name = "siderea-test-${System.nanoTime()}.zip"
        val uri = MediaStorePublisher(context).publish(zip, name, "application/zip", PublishKind.DOWNLOAD)
        try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(2_000L, it.getLong(0))
            }
        } finally {
            assertNotNull(uri)
            context.contentResolver.delete(uri, null, null)
        }
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 480
    }
}
