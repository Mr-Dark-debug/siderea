# Gallery and Sky Implementation Plan

**Goal:** Add gallery management, approachable sky capture and astro timelapse, and clearer settings/update UX.

**Architecture:** Native Compose screens call focused policies and repositories. Gallery mutations revalidate ownership and session state; sky UI/calculator/capture share one policy and persist compatible defaults.

**Tech Stack:** Kotlin, Compose, Camera2, MediaStore, Android ImageDecoder/Canvas, existing capture/export services.

**Constraints:** Android 10+, existing persistent signing certificate, originals preserved on edit, no deletion during capture/export, honest device capability labels, no Pixel processing claims. Execute inline under user authorization.

### 1. Gallery management

- [x] Add `SessionHandle.removeMedia(name, raw)` with source-name validation, RUNNING rejection and durable journal/manifest updates. Test paired-media retention and reopening without resurrection in SessionStoreTest.
- [x] Add `PhotoEdit`, `PhotoRenderer`, `GalleryEditor` for crop/rotation/mirror and color controls. The editor calls `GalleryRepository.saveCopy(photo, edit)`; return the saved GalleryPhoto, retain source date and EXIF and remove pending MediaStore rows on errors/cancellation.
- [x] Add repository deletion with owned MediaStore selection and session-path validation; reject active exports. Add processed JPEG listing. Add busy/error mutation state in GalleryViewModel.
- [x] Wire viewer edit/delete, grid selection/share/delete, confirmation and refreshed viewer items. Verify originals unchanged, new copy geometry and delete behavior on emulator.

### 2. Sky shooting

- [x] Add serializable preset/config defaults and `SkyCapturePolicy` with exposure clamp, far focus, interval calculation and mode-specific launch setup. Host tests compare UI cadence to persisted launch config and verify limited lenses.
- [x] Wire named presets, short summary, duration and advanced controls into AstroPanel. Add Astro option to TimelapsePanel and persist session kind/config. Implement cancelable setup countdown.
- [x] Choose default session processing from recorded preset. Run AstroFlowTest and new astro-timelapse capture/export tests.

### 3. Settings and updates

- [x] Replace verbose cards with concise groups/rows; add reset confirmation and dedicated Updates page with status/version/check time/release link and consistent retry/install actions.
- [x] Keep updater integrity policy and source-permission flow; add UI navigation and accessibility checks.

### 4. Verification and release

- [x] Run `./gradlew.bat qualityCheck testDebugUnitTest :app:lint :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --max-workers=2 --console=plain`; inspect every exit status and test report.
- [x] Install finished debug APKs on siderea_api36 and run affected instrumentation. Review actual screenshots at standard and 320dp widths; restore emulator display size afterwards.
- [x] Update docs/version to final 1.2.3, commit reviewed implementation, push main/tag under existing release authorization, await release workflow. GitHub CI and release runs both passed; no manual publication was needed.
- [x] Download actual published APK/checksum, verify version/certificate, use the app's updater to replace the retained v1.1.2 installation through v1.2.1 then v1.2.3 on the disposable emulator and verify retained photos/session/preferences. Record exact evidence and unverified real-phone/sky limits in `docs/VALIDATION-1.2.md`.
