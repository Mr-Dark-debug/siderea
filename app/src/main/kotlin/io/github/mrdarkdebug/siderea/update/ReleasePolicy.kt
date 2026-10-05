package io.github.mrdarkdebug.siderea.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.security.MessageDigest

const val RELEASE_REPO = "Mr-Dark-debug/siderea"
const val MAX_APK_BYTES = 100L * 1024 * 1024

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long,
    val digest: String? = null,
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

data class AppUpdate(
    val version: String,
    val code: Long,
    val asset: ReleaseAsset,
    val sha256: String,
)

object ReleasePolicy {
    private val json = Json { ignoreUnknownKeys = true }
    private val versionPattern = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
    private val digestPattern = Regex("^sha256:([a-fA-F0-9]{64})$")

    fun versionCode(version: String): Long? {
        val match = versionPattern.matchEntire(version) ?: return null
        val numbers = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
        val (major, minor, patch) = numbers
        if (major > MAX_MAJOR || minor > MAX_COMPONENT || patch > MAX_COMPONENT) return null
        return major * MAJOR_FACTOR + minor * MINOR_FACTOR + patch
    }

    fun parse(
        body: String,
        installedCode: Long,
    ): AppUpdate? {
        val release = json.decodeFromString<GitHubRelease>(body)
        if (release.draft || release.prerelease) return null
        val code = versionCode(release.tag) ?: error("Unrecognized release version. Try again later.")
        if (code <= installedCode) return null
        return candidate(release, installedCode)
            ?: error("This release has no verified compatible APK yet. Try again later.")
    }

    fun candidate(
        release: GitHubRelease,
        installedCode: Long,
    ): AppUpdate? {
        if (release.draft || release.prerelease) return null
        val code = versionCode(release.tag) ?: return null
        if (code <= installedCode) return null
        val version = release.tag.removePrefix("v")
        val names = listOf("siderea-v$version.apk", "siderea-v$version-debug-signed.apk")
        val asset =
            names.firstNotNullOfOrNull { name -> release.assets.singleOrNull { it.name == name } } ?: return null
        if (asset.size !in 1..MAX_APK_BYTES) return null
        val digest = digestPattern.matchEntire(asset.digest.orEmpty())?.groupValues?.get(1) ?: return null
        val url = URI(asset.url)
        if (url.scheme != "https" || url.host != "github.com" || url.userInfo != null ||
            url.path != "/$RELEASE_REPO/releases/download/${release.tag}/${asset.name}"
        ) {
            return null
        }
        return AppUpdate(version, code, asset, digest.lowercase())
    }

    fun verifyDigest(
        bytes: ByteArray,
        expected: String,
    ): Boolean = MessageDigest.getInstance("SHA-256").digest(bytes).toHex() == expected.lowercase()

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private const val MAX_MAJOR = 200_000L
    private const val MAX_COMPONENT = 99L
    private const val MAJOR_FACTOR = 10_000L
    private const val MINOR_FACTOR = 100L
}
