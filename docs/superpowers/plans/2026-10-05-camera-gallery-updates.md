# Camera, Gallery and Updates Implementation Plan

**Goal:** Release Siderea 1.1.2 with a simpler camera, calendar gallery and verified GitHub updates.

**Architecture:** Keep Camera2 and capture services; add a gallery repository/ViewModel and an isolated
update repository/ViewModel. Compose navigation connects all three. Android installs verified APKs.

**Tech Stack:** Kotlin, Compose, MediaStore, existing SessionStore, HTTPS, GitHub Releases, FileProvider.

## Constraints

- Android API 29 minimum; preserve capability-based controls and red-night palette.
- No invented image metadata, broad storage permission, silent install or automatic uninstall.
- Persistent signing, strict APK checksum/package/version/certificate validation.

## Camera

- [x] Modify CameraScreen, Viewfinder and CameraState: Auto/Pro, Tools sheet, lens overlay, gallery target.
- [x] Wire GalleryRoute into SideriaNavHost and Settings; shorten redundant settings copy.
- [x] Preserve existing capture/mode transitions; verify small-screen and accessibility targets.

## Gallery

- [x] Add GalleryModels, GalleryRepository, GalleryViewModel, GalleryScreen, GalleryViewer.
- [x] Query only app-owned images in Pictures/Siderea; include SessionStore frame JPEG/DNG files.
- [x] Test epoch-to-local-day grouping, leap-year calendar and day filtering.
- [x] Verify capture -> gallery -> calendar -> viewer -> details/share on emulator.

## Updates

- [x] Add ReleasePolicy, UpdateRepository, UpdateViewModel and UpdateUi.
- [x] Add Internet/network/install permissions and private updates FileProvider path.
- [x] Test stable-release selection, version bounds, digest validation, corrupted download rejection.
- [x] Pin CI debug signing key in a repository secret and reject release builds without a persistent key.
- [x] Verify download/installer consent, same-key upgrade, data retention and signer rejection on emulator.

## Release

- [x] Run `./gradlew qualityCheck testDebugUnitTest lint assembleDebug assembleRelease assembleDebugAndroidTest`.
- [x] Run focused instrumentation plus existing accessibility and camera smoke tests.
- [x] Update changelog, screenshots, README and release/update documentation with actual evidence.
- [x] Commit and push reviewed changes, publish v1.1.2 and inspect CI. The queued release job was cancelled; the verified local APK was published directly, and the final source CI status is recorded in the validation report.
- [x] Download the published APK, compare SHA-256/signature, install and smoke-test it on the emulator.
