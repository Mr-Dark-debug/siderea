package io.github.mrdarkdebug.siderea.ui.inspector

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Writes a report into the app cache and hands back a shareable content URI. */
class ReportExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** @throws IOException if the cache can't be written (for example, the disk is full). */
        suspend fun writeForSharing(
            json: String,
            deviceLabel: String,
        ): Uri =
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, REPORT_DIR).apply { mkdirs() }
                // Only the latest report is ever shared; don't let old ones pile up in the cache.
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, fileName(deviceLabel))
                file.writeText(json)
                FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
            }

        companion object {
            private const val REPORT_DIR = "reports"

            /** `siderea-capabilities-google-pixel-9-pro.json`: safe on every filesystem. */
            fun fileName(deviceLabel: String): String {
                val slug =
                    deviceLabel
                        .lowercase()
                        .replace(Regex("[^a-z0-9]+"), "-")
                        .trim('-')
                        .ifEmpty { "device" }
                        .take(MAX_SLUG)
                return "siderea-capabilities-$slug.json"
            }

            private const val MAX_SLUG = 48
        }
    }
