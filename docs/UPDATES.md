# App updates

Siderea checks the public stable release at
`https://api.github.com/repos/Mr-Dark-debug/siderea/releases/latest` when the app enters the foreground,
at most once per six hours after a successful check. Settings offers an immediate manual check and an
Automatic updates switch. No GitHub login is needed. Automatic downloads use unmetered connections;
on mobile data use Download in Settings. Downloads can be cancelled and failed downloads can be retried.

A release must contain exactly the expected versioned APK, a GitHub SHA-256 digest and a reasonable size.
The client limits metadata/download sizes, accepts only HTTPS and known GitHub redirect hosts, hashes
the complete APK, checks its byte count, package, increasing version code and signing certificate.
Private installer files are verified again before opening Android's package installer. A changed,
wrong-package or differently signed file never reaches the installer. Installation needs Android's
"Allow from this source" permission and the user's final approval. Cancelling does not uninstall Siderea.
Reopening the app restores a verified pending download; obsolete APKs are cleaned after replacement.

## Signing and the one-time migration

The public v1.0.0 APK used an ephemeral GitHub Actions debug key. Its certificate SHA-256 is
`34f7ab4b13684fb781406991c4bb157207b5db1acbaefed8c5d54a21979d7e8b`.
Only the APK, not its private signing key, was retained in the available artifacts. Android requires the
old private key to produce a compatible update; changing version numbers cannot bypass this.

The initial v1.1.0 CI APK also used a different key despite the key being configured. Its certificate was
`7eaa4be14445608315d24b6f1c6db8a832c4234459f48c3b138b98f42ad4550e`.
End-to-end verification caught this and the updater rejected that APK. v1.1.0 is marked prerelease.

From v1.1.2 the debug key is persistent, held outside Git and in the repository's `DEBUG_KEYSTORE_BASE64`
Actions secret. Its certificate SHA-256 is
`f11e976967911c8e585dd88817d6587076a802840699eebf7e3c8304bedbe3b5`.
Gradle receives the exact keystore path. CI compares the APK's certificate with the configured key and
refuses publication on a mismatch. Back up the existing key before changing machines.
Release signing secrets take precedence when configured; a key switch still needs a migration.

If Android says v1.1.2 conflicts with v1.0.0 or the initial v1.1.0, **export your sessions as ZIPs first**,
then reinstall manually.
Uninstalling removes private sessions and preferences. Shared photos in `Pictures/Siderea` remain on disk,
but Android may no longer attribute the old images to the reinstalled app: view them in the system gallery.
The in-app gallery intentionally requests no access to other apps' images. If your installed build already
uses the persistent local key, installing v1.1.2 over it preserves data. Subsequent releases signed with this
key can use the in-app updater.

## Research

- [Android app-owned media and scoped storage](https://developer.android.com/training/data-storage/shared/media)
- [Android package installer and user confirmation](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams)
- [GitHub stable releases API and asset digests](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)
- [Android signing and update compatibility](https://developer.android.com/studio/publish/app-signing)

## Reproducible verification

Host checks: `gradlew qualityCheck testDebugUnitTest lint assembleRelease`.
Focused emulator checks: GalleryFlowTest, UpdateIntegrityTest, SideriaSmokeTest and AccessibilityTest.
For the complete production updater, build an isolated lower-version fixture from the same source and key
(`-Psiderea.versionName=1.0.99`), install it on a disposable emulator, capture a photo and change a preference.
Open the published GitHub update through Settings; approve Android's source permission and installation.
After restart verify version 1.1.2, retained photo/preference/session data and obsolete installer cleanup.
The lower-version fixture is test setup, not evidence that the old differently signed v1.0.0 can be replaced.
