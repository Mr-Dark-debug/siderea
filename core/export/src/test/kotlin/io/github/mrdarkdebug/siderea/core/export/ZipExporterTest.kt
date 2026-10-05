package io.github.mrdarkdebug.siderea.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipExporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(
        name: String,
        content: ByteArray,
    ): File = tmp.newFile(name).also { it.writeBytes(content) }

    @Test
    fun archiveContainsEveryFileUnderItsName() {
        val a = file("a.jpg", ByteArray(2_000) { it.toByte() })
        val b = file("session.json", "{\"a\":1}".repeat(500).toByteArray())
        val out = File(tmp.root, "out/frames.zip")
        val progress = ArrayList<Pair<Int, Int>>()
        ZipExporter.zip(listOf(ZipSource("jpeg/IMG_000001.jpg", a), ZipSource("session.json", b)), out) { d, t ->
            progress += d to t
        }
        ZipFile(out).use { zip ->
            assertEquals(2, zip.size())
            assertEquals(
                a.readBytes().toList(),
                zip.getInputStream(zip.getEntry("jpeg/IMG_000001.jpg")).readBytes().toList(),
            )
            assertEquals(b.readBytes().toList(), zip.getInputStream(zip.getEntry("session.json")).readBytes().toList())
        }
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun jpegIsStoredAndJsonIsCompressed() {
        val jpg = file("a.jpg", ByteArray(5_000) { (it * 7).toByte() })
        val json = file("session.json", "abcdefgh".repeat(1_000).toByteArray())
        val out = File(tmp.root, "x.zip")
        ZipExporter.zip(listOf(ZipSource("a.jpg", jpg), ZipSource("session.json", json)), out)
        ZipFile(out).use { zip ->
            val jpgEntry = zip.getEntry("a.jpg")
            val jsonEntry = zip.getEntry("session.json")
            assertTrue("jpeg must not shrink", jpgEntry.compressedSize >= jpgEntry.size)
            assertEquals(ZipEntry.DEFLATED, jsonEntry.method)
            assertTrue("json must shrink", jsonEntry.compressedSize < jsonEntry.size / 4)
        }
    }

    @Test
    fun aMissingSourceLeavesNoHalfWrittenArchive() {
        val good = file("a.jpg", ByteArray(10))
        val out = File(tmp.root, "broken.zip")
        assertThrows(Exception::class.java) {
            ZipExporter.zip(listOf(ZipSource("a.jpg", good), ZipSource("gone.jpg", File(tmp.root, "gone.jpg"))), out)
        }
        assertFalse(out.exists())
    }
}
