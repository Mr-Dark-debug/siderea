# Camera, Gallery and Updates Implementation Plan

**Goal:** Release Siderea 1.1.0 with a simpler camera, calendar gallery and verified GitHub updates.

**Architecture:** Keep Camera2 and capture services; add a gallery repository/ViewModel and an isolated
update repository/ViewModel. Compose navigation connects all three. Android installs verified APKs.

**Tech Stack:** Kotlin, Compose, MediaStore, existing SessionStore, HTTPS, GitHub Releases, FileProvider.

## Constraints

- Android API 29 minimum; preserve capability-based controls and red-night palette.
- No invented image metadata, broad storage permission, silent install or automatic uninstall.
- Persistent signing, strict APK checksum/package/version/certificate validation.

## Camera

- [ ] Modify CameraScreen, Viewfinder and CameraState: Auto/Pro, Tools sheet, lens overlay, gallery target.
- [ ] Wire GalleryRoute into SideriaNavHost and Settings; shorten redundant settings copy.
- [ ] Preserve existing capture/mode transitions; verify small-screen and accessibility targets.

## Gallery

- [ ] Add GalleryModels, GalleryRepository, GalleryViewModel, GalleryScreen, GalleryViewer.
- [ ] Query only app-owned images in Pictures/Siderea; include SessionStore frame JPEG/DNG files.
- [ ] Test epoch-to-local-day grouping, leap-year calendar and day filtering.
- [ ] Verify capture -> gallery -> calendar -> viewer -> details/share on emulator.

## Updates

- [ ] Add ReleasePolicy, UpdateRepository, UpdateViewModel and UpdateUi.
- [ ] Add Internet/network/install permissions and private updates FileProvider path.
- [ ] Test stable-release selection, version bounds, digest validation, corrupted download rejection.
- [ ] Pin CI debug signing key in a repository secret and reject release builds without a persistent key.
- [ ] Verify download/installer consent, same-key upgrade, data retention and signer rejection on emulator.

## Release

- [ ] Run `./gradlew qualityCheck testDebugUnitTest lint assembleDebug assembleRelease assembleDebugAndroidTest`.
- [ ] Run focused instrumentation plus existing accessibility and camera smoke tests.
- [ ] Update changelog, screenshots, README and release/update documentation with actual evidence.
- [ ] Commit and push reviewed changes, publish v1.1.0 through the release workflow and inspect CI.
- [ ] Download the published APK, compare SHA-256/signature, install and smoke-test it on the emulator.
