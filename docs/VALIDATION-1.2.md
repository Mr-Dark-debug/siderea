# Siderea 1.2.2 validation

Validation date: 2026-10-07. Environment: Windows, JDK 21, Android API 36 emulator
`siderea_api36`. This report distinguishes local checks, emulator behavior and published release verification.

## Scope

- Gallery editor, single/batch deletion and sharing; date/calendar browsing retained.
- Sky presets and advanced controls; Moon photo averaging without star alignment.
- Timelapse → Astro capture, persisted configuration and video export.
- Settings, confirmed reset and dedicated Updates page.

## Build and host checks

The complete release candidate passed:

```powershell
./gradlew.bat qualityCheck testDebugUnitTest lint assembleDebug assembleDebugAndroidTest assembleRelease --max-workers=2 --console=plain
```

All 319 host tests passed with zero failures/errors. This includes preset clamping,
cadence and configuration compatibility, session-journal deletion, camera policies, processing and updater integrity.
ktlint and detekt reported no violations. Android lint reported zero errors. Debug, instrumentation and
optimized release APKs built successfully, including R8/resource shrinking. After adapting the existing
accessibility/smoke tests to the simpler controls, quality checks and the test APK build were rerun successfully.

Final local release: package `io.github.mrdarkdebug.siderea`, version `1.2.2` (10202). The optimized APK's
certificate SHA-256 is `f11e976967911c8e585dd88817d6587076a802840699eebf7e3c8304bedbe3b5`, matching v1.1.2.

## Emulator checks

All 42 distinct focused instrumentation checks passed across the main runs and final gallery rechecks:
PhotoEditingTest (3), GallerySafetyTest (5), GalleryFlowTest (7), AstroFlowTest (3),
SkyTimelapseFlowTest (2), AccessibilityTest (4), SideriaSmokeTest (10), TimelapseFlowTest (4),
LongExposureFlowTest (1) and UpdateIntegrityTest (3).

- Pixel geometry for rotate/mirror/crop; brightness/color changes without altering input pixels.
- Real editor save: crop/rotation dimensions, original bytes unchanged and EXIF capture date retained.
- Old captures without a source timezone offset retain the exact indexed epoch, including milliseconds,
  after publishing an edited copy; the library uses that original date.
- Cancel/confirm deletion, final image in a filtered viewer, batch deletion, date/calendar filtering,
  swiping/back and publication refresh.
- Durable source JPEG deletion with paired RAW retained, active capture rejected, processed-result deletion,
  and refusal to delete media outside the app's Pictures/Siderea library.
- Astro capture, star-trail JPEG/TIFF, ten dark frames, Moon averaging JPEG, Astro timelapse configuration
  and MP4 export. Normal timelapse video/ZIP and all three Bulb combining modes also passed.
- Settings, confirmed reset, preference persistence, updater navigation and Android accessibility checks
  for mode panels, gallery/calendar, viewer/editor and Updates.
- Corrupt pending update rejection/cleanup, obsolete installer cleanup and installed APK upgrade rejection.

The first full run passed 40 tests and exposed an assumption in the final-image gallery test: Android's
asynchronous EXIF scan temporarily dated another fixture by its added date. The fixture now explicitly isolates
the selected day's single image and clears other debug-package session fixtures and app-owned shared images. The entire seven-test
gallery class then passed. Production storage was kept separate from all debug/test fixtures.

All eleven gallery/accessibility checks also passed at 320 dp width (720 px, density 360).
Visual review then removed duplicate photo controls from sky capture and made all four Astro presets visible
in a two-column layout. All 24 affected camera/settings/capture/export checks were repeated after this visual polish
and passed, including the sky delay button and retaining a custom capture length when reopening Astro.

## Published release and replacement

The v1.2.0 [CI](https://github.com/Mr-Dark-debug/siderea/actions/runs/37676574153) and
[release workflow](https://github.com/Mr-Dark-debug/siderea/actions/runs/37676577171) both passed.
Screenshot review of that build then caught an unstyled updater label inheriting black instead of the night
palette's text color. The v1.2.1 patch provides the active palette's default Material content color throughout
the theme. Functional capture/gallery code is unchanged. The patch passed all build/quality/lint gates and
319 host tests. All 26 repeated emulator checks passed across the patch run and isolated gallery recheck,
and all four camera/settings accessibility checks passed again at 320 dp. Actual screenshots were reviewed
in amber and red palettes.

The v1.2.1 [CI](https://github.com/Mr-Dark-debug/siderea/actions/runs/37679208259) and
[release workflow](https://github.com/Mr-Dark-debug/siderea/actions/runs/37679212720) both passed.
Its downloaded public APK and sidecar matched GitHub's asset digest and byte count (3,741,571 bytes),
with SHA-256 `0f3639f86f7df06610f99a808da3512ba8a88240da4618633b4f49007adf10ab` and the persistent signer above.
The intact production v1.1.2 app automatically checked GitHub and downloaded exactly that APK. Its private
download matched the published hash. The app opened Android's Update confirmation, installation completed,
and the new app reported 1.2.1 (10201). Before further capture or setting changes, all ten session files,
the 48-byte preference file and both shared photos were byte-identical to the pre-upgrade snapshots.
The downloaded installer and pending metadata were removed on reopening. A manual check then reported
You're up to date; a new capture appeared with the six retained images. Debug/test storage remained separate.

The published editor review caught a calendar issue that the original EXIF-only assertion missed:
Android cleared DATE_TAKEN when rescanning an older edited JPEG without OffsetTimeOriginal. The v1.2.2
patch writes an explicit offset and millisecond field from the source's known capture instant when needed.
An added regression verifies both the MediaStore indexed timestamp and the repository date, with unchanged
original bytes. Calendar fixtures now include explicit offsets too. The Android
[DATE_TAKEN contract](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#DATE_TAKEN)
and [scanner source](https://android.googlesource.com/platform/packages/providers/MediaProvider/+/f2abe4aec018f0522b4b1303fb25351db0604eb5/src/com/android/providers/media/scan/ModernMediaScanner.java)
explain why EXIF without an offset is insufficient for old copies. Final v1.2.2 publication and replacement
evidence will be recorded below after validation.

The final v1.2.2 candidate passed the complete build/quality/lint command and all 319 host tests again.
All twelve gallery safety/flow checks passed, including the new old-date regression. The first regression
run compared the primary-volume insertion URI with the library's aggregate-volume URI; the timestamp
assertion passed, but that test-only identity assumption was corrected to compare the unique copy name.

## Limits

No real-phone camera, real-night-sky image quality or OEM installer checks have been performed.
The emulator validates workflows, stored configuration, pixel transformations, media ownership and export,
using its virtual camera rather than a star field. Synthetic host tests cover star detection/alignment.
Presets are starting points within reported lens limits. Siderea does not include Google's proprietary
Night Sight or photo-editing models. Editing creates bounded JPEG copies up to 8 MP and retains the originals.
Astro video export is an explicit action after recording rather than simultaneous capture/export.

See [gallery and sky usage](GALLERY-SKY.md), [updates](UPDATES.md) and [release procedure](RELEASING.md).
