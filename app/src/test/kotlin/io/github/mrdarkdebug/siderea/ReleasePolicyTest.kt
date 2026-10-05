package io.github.mrdarkdebug.siderea

import io.github.mrdarkdebug.siderea.update.GitHubRelease
import io.github.mrdarkdebug.siderea.update.MAX_APK_BYTES
import io.github.mrdarkdebug.siderea.update.RELEASE_REPO
import io.github.mrdarkdebug.siderea.update.ReleaseAsset
import io.github.mrdarkdebug.siderea.update.ReleasePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePolicyTest {
    @Test(expected = IllegalStateException::class)
    fun newerReleaseWithoutVerifiedApkDoesNotClaimUpToDate() {
        ReleasePolicy.parse("""{"tag_name":"v1.2.0","assets":[]}""", 10100)
    }

    private val digest = "a".repeat(64)

    private fun release(tag: String = "v1.2.0") =
        GitHubRelease(
            tag,
            assets =
                listOf(
                    ReleaseAsset(
                        "siderea-$tag-debug-signed.apk",
                        "https://github.com/$RELEASE_REPO/releases/download/$tag/siderea-$tag-debug-signed.apk",
                        123,
                        "sha256:$digest",
                    ),
                ),
        )

    @Test fun onlyNewStableVersionsAreSelected() {
        assertEquals(10200L, ReleasePolicy.candidate(release(), 10100)?.code)
        assertNull(ReleasePolicy.candidate(release(), 10200))
        assertNull(ReleasePolicy.candidate(release().copy(prerelease = true), 0))
        assertNull(ReleasePolicy.candidate(release().copy(draft = true), 0))
        listOf("v1.2.0-beta", "v1.100.0", "v1.2.100", "v999999999999999999.0.0", "v01.2.0")
            .forEach { assertNull(ReleasePolicy.versionCode(it)) }
    }

    @Test fun assetMustHaveDigestSizeAndExactRepositoryAddress() {
        val base = release()
        listOf(
            base.assets.single().copy(digest = null),
            base.assets.single().copy(size = MAX_APK_BYTES + 1),
            base.assets.single().copy(url = "https://evil.example/app.apk"),
            base.assets.single().copy(
                url =
                    base.assets
                        .single()
                        .url
                        .replace("https:", "http:"),
            ),
            base.assets.single().copy(name = "other.apk"),
            base.assets.single().copy(size = 0),
        ).forEach { assertNull(ReleasePolicy.candidate(base.copy(assets = listOf(it)), 0)) }
    }

    @Test fun tamperedBytesFailDigestVerification() {
        val known = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(ReleasePolicy.verifyDigest("abc".toByteArray(), known))
        assertFalse(ReleasePolicy.verifyDigest("abd".toByteArray(), known))
    }

    @Test fun realGitHubJsonIgnoresUnrelatedFields() {
        val body = """{"tag_name":"v1.2.0","draft":false,"prerelease":false,"body":"notes","assets":[
            {"name":"siderea-v1.2.0.apk","browser_download_url":"https://github.com/$RELEASE_REPO/releases/download/v1.2.0/siderea-v1.2.0.apk","size":12,"digest":"sha256:$digest"}]}"""
        assertEquals("1.2.0", ReleasePolicy.parse(body, 10100)?.version)
    }
}
