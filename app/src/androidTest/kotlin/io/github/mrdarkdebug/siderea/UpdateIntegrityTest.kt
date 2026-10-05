package io.github.mrdarkdebug.siderea

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mrdarkdebug.siderea.update.UpdateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateIntegrityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = UpdateRepository(context)

    @Test fun corruptedPendingUpdateIsRejectedAndCleaned() =
        runBlocking {
            val file = File(context.filesDir, "updates/update.apk")
            file.parentFile!!.mkdirs()
            file.writeBytes("tampered".toByteArray())
            repository.prefs
                .edit()
                .putLong("pending_code", repository.installedCode + 1)
                .putLong("pending_size", file.length())
                .putString("pending_hash", "0".repeat(64))
                .commit()
            assertNull(repository.pending())
            assertFalse(file.exists())
            assertFalse(repository.prefs.contains("pending_code"))
        }

    @Test fun obsoleteInstallerIsCleanedAfterUpgrade() =
        runBlocking {
            val directory = File(context.filesDir, "updates").apply { mkdirs() }
            val apk = File(directory, "update.apk").apply { writeText("obsolete") }
            val part = File(directory, "update.part").apply { writeText("interrupted") }
            repository.prefs
                .edit()
                .putLong("pending_code", repository.installedCode)
                .commit()
            assertNull(repository.pending())
            assertFalse(apk.exists())
            assertFalse(part.exists())
        }

    @Test fun installedApkCannotBeOfferedAsAnUpgrade() {
        val installed = File(context.applicationInfo.sourceDir)
        assertTrue(runCatching { repository.validateApk(installed, repository.installedCode) }.isFailure)
    }
}
