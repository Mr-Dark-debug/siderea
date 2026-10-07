# Siderea 1.2.0 validation

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

Local release: package `io.github.mrdarkdebug.siderea`, version `1.2.0` (10200). The optimized APK's
certificate SHA-256 is `f11e976967911c8e585dd88817d6587076a802840699eebf7e3c8304bedbe3b5`, matching v1.1.2.

## Emulator checks

All 41 distinct focused instrumentation checks passed across the main run and the final gallery recheck:
PhotoEditingTest (3), GallerySafetyTest (4), GalleryFlowTest (7), AstroFlowTest (3),
SkyTimelapseFlowTest (2), AccessibilityTest (4), SideriaSmokeTest (10), TimelapseFlowTest (4),
LongExposureFlowTest (1) and UpdateIntegrityTest (3).

- Pixel geometry for rotate/mirror/crop; brightness/color changes without altering input pixels.
- Real editor save: crop/rotation dimensions, original bytes unchanged and EXIF capture date retained.
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
the selected day's single image and clears other debug-package session fixtures. The entire seven-test
gallery class then passed. Production storage was kept separate from all debug/test fixtures.

All eleven gallery/accessibility checks also passed at 320 dp width (720 px, density 360).
Visual review then removed duplicate photo controls from sky capture and made all four Astro presets visible
in a two-column layout. All 24 affected camera/settings/capture/export checks were repeated after this visual polish
and passed, including the sky delay button and retaining a custom capture length when reopening Astro.

## Published release and replacement

Pending final verification and publication. The existing production v1.1.2 installation remains intact
for a same-key upgrade check; debug/test packages use separate storage.

## Limits

No real-phone camera, real-night-sky image quality or OEM installer checks have been performed.
The emulator validates workflows, stored configuration, pixel transformations, media ownership and export,
using its virtual camera rather than a star field. Synthetic host tests cover star detection/alignment.
Presets are starting points within reported lens limits. Siderea does not include Google's proprietary
Night Sight or photo-editing models. Editing creates bounded JPEG copies up to 8 MP and retains the originals.
Astro video export is an explicit action after recording rather than simultaneous capture/export.

See [gallery and sky usage](GALLERY-SKY.md), [updates](UPDATES.md) and [release procedure](RELEASING.md).
