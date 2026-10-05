package io.github.mrdarkdebug.siderea.update

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mrdarkdebug.siderea.update.ReleasePolicy.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton
class UpdateRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val directory = File(context.filesDir, "updates")
        val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        val installedCode: Long get() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

        fun unmetered(): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }

        suspend fun check(): AppUpdate? =
            withContext(Dispatchers.IO) {
                val connection = open("https://api.github.com/repos/$RELEASE_REPO/releases/latest")
                try {
                    if (connection.responseCode == HTTP_NOT_FOUND) return@withContext null
                    require(
                        connection.responseCode == HTTP_OK,
                    ) { "GitHub is unavailable (${connection.responseCode}). Try again later." }
                    val bytes =
                        connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(
                                    output.size() + count <= MAX_METADATA_BYTES,
                                ) { "Release information is too large." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    ReleasePolicy.parse(bytes.toString(Charsets.UTF_8), installedCode)
                } finally {
                    connection.disconnect()
                }
            }

        suspend fun download(
            update: AppUpdate,
            progress: (Float) -> Unit,
        ): File =
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                val part = File(directory, "update.part")
                val target = File(directory, "update.apk")
                part.delete()
                val connection = open(update.asset.url)
                try {
                    require(connection.responseCode == HTTP_OK) { "Couldn't download the update. Try again." }
                    val hash = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    connection.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                require(
                                    total <= update.asset.size && total <= MAX_APK_BYTES,
                                ) { "Update exceeds its expected size." }
                                out.write(buffer, 0, read)
                                hash.update(buffer, 0, read)
                                progress(total.toFloat() / update.asset.size)
                            }
                        }
                    }
                    require(total == update.asset.size && hash.digest().toHex() == update.sha256) {
                        "Update verification failed. Download it again."
                    }
                    validateApk(part, update.code)
                    target.delete()
                    require(part.renameTo(target)) { "Couldn't save the update." }
                    prefs
                        .edit()
                        .putString("pending_version", update.version)
                        .putLong("pending_code", update.code)
                        .putString("pending_hash", update.sha256)
                        .putLong("pending_size", update.asset.size)
                        .apply()
                    target
                } finally {
                    connection.disconnect()
                    part.delete()
                }
            }

        suspend fun pending(): File? =
            withContext(Dispatchers.IO) {
                File(directory, "update.part").delete()
                val file = File(directory, "update.apk")
                val code = prefs.getLong("pending_code", 0)
                if (code <= installedCode) {
                    file.delete()
                    File(directory, "update.part").delete()
                    clearPending()
                    return@withContext null
                }
                if (!file.isFile) {
                    clearPending()
                    return@withContext null
                }
                runCatching {
                    verifyPending(file, code)
                    file
                }.getOrElse {
                    file.delete()
                    clearPending()
                    null
                }
            }

        suspend fun installerFile(): File =
            withContext(Dispatchers.IO) {
                val file = File(directory, "update.apk")
                verifyPending(file, prefs.getLong("pending_code", 0))
                file
            }

        private fun verifyPending(
            file: File,
            code: Long,
        ) {
            require(
                file.isFile && file.length() == prefs.getLong("pending_size", -1),
            ) { "Update file is missing. Download again." }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            require(
                digest.digest().toHex() == prefs.getString("pending_hash", null),
            ) { "Update file changed. Download again." }
            validateApk(file, code)
        }

        fun validateApk(
            file: File,
            expectedCode: Long,
        ) {
            val manager = context.packageManager
            val apk =
                manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
                    ?: throw IOException("This download is not a valid Android app.")
            val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            require(
                apk.packageName == installed.packageName && apk.longVersionCode == expectedCode &&
                    apk.longVersionCode > installed.longVersionCode,
            ) { "This update does not match the installed app." }
            val oldSigners =
                installed.signingInfo
                    ?.apkContentsSigners
                    .orEmpty()
                    .map { it.toCharsString() }
                    .toSet()
            val newSigners =
                apk.signingInfo
                    ?.apkContentsSigners
                    .orEmpty()
                    .map { it.toCharsString() }
                    .toSet()
            require(oldSigners.isNotEmpty() && oldSigners == newSigners) {
                "This update has a different signing key. Keep your current app and export sessions " +
                    "before a manual migration."
            }
        }

        private fun clearPending() {
            prefs
                .edit()
                .remove(
                    "pending_version",
                ).remove("pending_code")
                .remove("pending_hash")
                .remove("pending_size")
                .apply()
        }

        private fun open(url: String): HttpsURLConnection {
            var next = URI(url)
            repeat(MAX_REDIRECTS) {
                require(
                    next.scheme == "https" && next.userInfo == null && next.host in ALLOWED_HOSTS,
                ) { "Untrusted download address." }
                val connection = next.toURL().openConnection() as HttpsURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Siderea-Android")
                if (next.host ==
                    "api.github.com"
                ) {
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                }
                if (connection.responseCode in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    require(!location.isNullOrBlank()) { "Invalid download redirect." }
                    next = next.resolve(location)
                } else {
                    return connection
                }
            }
            throw IOException("Too many download redirects.")
        }

        private companion object {
            val ALLOWED_HOSTS =
                setOf(
                    "api.github.com",
                    "github.com",
                    "release-assets.githubusercontent.com",
                    "objects.githubusercontent.com",
                )
            val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
            const val HTTP_OK = 200
            const val HTTP_NOT_FOUND = 404
            const val MAX_REDIRECTS = 5
            const val TIMEOUT_MS = 20_000
            const val MAX_METADATA_BYTES = 1_048_576
            const val BUFFER_SIZE = 64 * 1024
        }
    }
