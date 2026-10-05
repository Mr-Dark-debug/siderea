# Changelog

All notable changes are recorded here. Each milestone lists **what works**, **what has not been tried on a
real device**, and **known issues**. Format follows [Keep a Changelog](https://keepachangelog.com/);
versions follow [Semantic Versioning](https://semver.org/).

## [0.3.0] - 2026-10-05

**Milestone M2: timelapse, sessions, foreground service, resume.** Timelapse mode on the mode strip is now
real. There is no video export yet (M3): frames and `session.json` are in app storage for now.

### What works
- **Timelapse capture** on the Camera2 engine from a **foreground service** (type camera, partial wake lock,
  ongoing notification with a Stop action). Intervals by preset or ruler, stop after N frames, a duration or
  never; exposure and focus locked from the current settings so frames stay consistent.
- **Absolute schedule:** frame *n* is due at start + *n* × interval, so there is no drift. An overrun
  re-anchors the schedule and tells you.
- **Calculator** (shown first in the panel): minimum interval from a **measured** capture overhead (estimated
  until the first frames are in, remembered afterwards), frame count, video length at a chosen fps, storage and
  battery estimates.
- **Pre-flight checklist**: interval vs. overhead, storage, battery, a steady-phone check from the gyro,
  focus locked, exposure locked, airplane mode, exact-alarm and battery-optimisation fixes, notifications.
- **Guards:** heat (slow down, pause, stop), battery, low storage. Each says what it did in plain words.
- **Doze handling:** waits of 20 s or more use `AlarmManager` allow-while-idle alarms; a *keep screen on
  (dimmed)* mode avoids Doze entirely.
- **Sessions:** `yyyy-MM-dd_HHmm_Kind/{raw,jpeg,previews,darks,exports}` folders, `session.json` written
  atomically, and a crash-safe `frames.jsonl` journal merged on load (a truncated last line is ignored). Each
  frame records the actual `CaptureResult` values. Sessions list and detail screens (previews, rename, delete).
- **Interrupted sessions:** after a crash or kill, the next launch offers **Resume** (continues numbering and
  schedule) or **Finalize what was captured**.

### Verified
- 227 host unit tests (adds sessions, journal recovery, interval maths, guards, pre-flight).
- 33 emulator tests on Android 16 (adds the real foreground service writing a session to disk and a UI test
  for pre-flight → run → stop → session detail).
- Manually on the emulator: kill-and-resume (6 frames before the kill, continued to 10 after Resume) and a
  60 s run in **forced deep Doze with the screen off and unplugged**: 12 frames at a 5 s interval, none lost.
- ktlint, detekt, Android lint and an R8 release build are clean. Lint caught two real crashes
  (`canScheduleExactAlarms` needs API 31) that the API 36 emulator hides.

### Untested on a real device
- Long runs: the 2-hour background session, thermal behaviour and the heat guard on a warm phone.
- Doze on hardware with a **30-minute interval** (the emulator's `force-idle` isn't the same as real standby).
  Intervals shorter than ~20 s in deep Doze remain unreliable without the dimmed-screen mode: this is Android,
  not something Siderea can fully remove.
- Camera taken by another app mid-session, storage-full behaviour, notification permission flows on Android 13+.
- Exact-alarm and battery-optimisation settings screens as shown by different manufacturers.

### Known issues
- No video export or contact sheet yet: arrives in M3 (v0.4.0).
- Resume after a **device reboot** has not been tried: the journal is on disk, but only an app kill was tested.
- A session's frame order is by timestamp; editing files by hand inside a session folder is not supported.
- Astro, Long exposure and the exposure ramp are still not built.

## [0.2.0] - 2026-10-05

**Milestone M1: photo mode.** The app now opens on a real viewfinder with full manual control. It was built
from the maintainer's Pixel 10 Capability Inspector report (Android 17), which changed several decisions.

### What works
- **Viewfinder and capture** on a dedicated Camera2 engine thread: live preview, JPEG, **RAW (DNG via
  `DngCreator`)** and RAW+JPEG, 4:3 or 16:9, saved to `Pictures/Siderea` through MediaStore.
- **Lens switcher that shows only lenses that exist.** On a Pixel 10: back 0.6x, 1x, 5x and front 0.9x, 1x. The
  default lens of a logical camera opens directly; the other lenses are bound as **physical camera streams**
  (`OutputConfiguration.setPhysicalCameraId`), with an automatic fall-back to zooming the logical camera and a
  message if the phone refuses.
- **Manual controls**, each with Auto/Manual: shutter (up to the lens's real maximum, 16 s on the Pixel 10 back
  camera), ISO, exposure compensation, focus (autofocus with tap-to-focus, or a manual dial with ∞ at the end)
  and white balance (presets, or Kelvin + tint). Shutter and ISO combine into P / S / I / M behaviour.
- **Software shutter-priority and ISO-priority** (the Pixel 10 has no hardware priority modes): a damped
  controller meters the preview and moves the free value.
- **Kelvin and tint without Android 16 CCT** (the Pixel 10 doesn't offer it): white balance is computed from the
  sensor's own colour calibration into per-channel gains and a colour matrix, using the same maths a DNG
  reader uses in reverse.
- **Long-exposure preview**: a manual 16 s shutter previews at a capped shutter with raised ISO and a display
  gain, so the viewfinder keeps moving; the photo uses the exact exposure. An exposure progress bar and a stop
  button run during long shots.
- **Aids:** live histogram with clipping markers, focus peaking, zebras, grid (thirds / centre), horizon level
  from the gravity sensor (with camera altitude for aiming at the sky), **night view** (brightens the
  viewfinder only), self-timer (2 / 5 / 10 s) and **volume-key / Bluetooth-remote shutter**.
- **Honest limits:** a lens that can't do something (the Pixel 10 ultra-wide is fixed-focus; the emulator's
  back camera has no manual sensor) shows the reason in the control instead of silently failing.
- Status line with battery, free space, thermal warning and capture resolution; camera permission screen;
  human-readable errors when another app takes the camera.
- Capability Inspector fixes from the Pixel 10 report: manual focus is no longer reported as unsupported when a
  camera hides its nearest-focus distance; the 5x lens is labelled 5x as in the stock camera; white-balance and
  hybrid-AE messages now say what Siderea does instead.

### Verified
- 172 host unit tests, including checks against the **real Pixel 10 report** (lens catalogue, limits, zoom
  labels, verdicts) and colour maths against published CIE values.
- 28 emulator tests on Android 16: engine tests open every lens, check live metadata, confirm a manual exposure
  in the sensor's own report, produce valid JPEG and (on lenses whose HAL supplies the metadata) valid DNG
  files through the physical-stream path, plus UI tests that take a real photo and find it in the gallery.
- ktlint, detekt, Android lint and an R8 release build are clean.

### Untested on a real device
Everything camera-related has only run on Android emulators. Specifically unverified on a Pixel:
- the physical-stream path for the 0.6x and 5x lenses, and whether per-physical settings reach the right sensor
- a full 16 s exposure, its preview behaviour and DNG creation from a physical camera's result
- the computed Kelvin/tint colour (it should look neutral; please compare with the stock app) and
  the colour matrix
- manual focus range (Siderea assumes 10 cm because the Pixel 10 reports none) and how exact ∞ is
- software S/I priority tracking, tap-to-focus mapping, thermal warnings, volume keys and Bluetooth remotes

### Known issues
- Timelapse, Astro and Long exposure are visible on the mode strip but **not built yet**; tapping one says when
  it arrives.
- The UI is portrait-only. Photos are still saved with the correct orientation when the phone is held sideways.
- No digital zoom and no flash.
- Photos go straight to the gallery. Sessions with `session.json` arrive in M2.
- On the emulator's virtual-scene back camera DngCreator rejects the capture metadata, so RAW there is untested.
- The front-camera preview is not mirrored.

## [0.1.0] - 2026-10-05

**Milestone M0: project skeleton, design system, logo, Capability Inspector.**
There is no camera viewfinder yet. This release exists to find out, on a real phone, exactly what its
cameras report: everything later is built on that data.

### What works
- **Capability Inspector.** Enumerates every camera, including physical sub-cameras behind logical ones, and
  shows lens facing, focal length, 35 mm equivalent and `0.5x / 1x / 5x` label, aperture, hardware level,
  capabilities, manual-sensor and RAW flags, shutter / ISO / frame-duration / exposure-compensation ranges,
  focus range, hyperfocal and minimum focus distance, sensor geometry, RAW / JPEG / YUV output sizes
  (including the maximum-resolution sensor mode), AE / AWB / AF / noise-reduction / edge / hot-pixel modes,
  Android 16 Kelvin + tint and hybrid auto-exposure support, and vendor camera extensions (Night, HDR…).
- **Per-lens verdicts** in plain language, with a next step whenever something isn't supported, for example
  "This lens can't expose longer than 1/2 s. Use Virtual Bulb for longer exposures."
- **Copy as JSON** and **Share report** (a `.json` file through the share sheet). Schema version 1.
- **Home** summary of the main camera, and an honest list of the modes that are *not built yet* with the release
  each arrives in.
- **Settings:** red night-vision mode, haptics, reset, About, auto-generated open-source licences.
- **Design system** (`:core:ui`): true-black amber palette and a pure-red night palette (WCAG AA contrast
  asserted by tests), mono readout typography, pill controls, readout bar, segmented pill, cards, haptic
  vocabulary, 56 dp touch targets.
- **Identity:** logo (SVG master, light/dark variants, wordmark), adaptive launcher icon with themed
  (monochrome) layer.
- **Build and CI:** Kotlin 2.4 / AGP 9.4 multi-module build with convention plugins, ktlint + detekt with zero
  tolerated findings, Android lint, R8-minified release build, GitHub Actions CI and tag-triggered release.
- Android 10 (API 29) minimum; targets Android 17 (API 37). Android 16 features are gated by version *and* by
  what each camera HAL reports.

### Verified
- 73 host unit tests (formatting, zoom labels, feature verdicts, JSON round-trip, inspector rows, summary,
  repository, settings, palette contrast).
- 18 instrumentation tests on an **Android 16 (API 36) emulator** and on an **Android 14 (API 34) emulator**:
  navigation, Inspector content, clipboard JSON, red-mode persistence across recreation, and the real reader's
  internal consistency.
- The minified release APK installs, launches, opens the Inspector and copies JSON without a crash (API 36).
- Red mode written to disk, process killed, relaunched: the whole UI comes back red (API 34).

### Untested on a real device
**Everything.** v0.1.0 has only ever run on Android emulators, whose virtual cameras report simplified
capabilities. Specifically unverified:
- That physical sub-cameras can be queried on your phone (some vendors refuse; this is reported as a
  per-camera error instead of a crash, but that path has only been exercised with synthetic data).
- Zoom labels and the "main lens" choice on real multi-lens phones.
- Android 16 Kelvin / tint / hybrid-AE reporting on a real Pixel HAL (the emulator reports CCT but no tint).
- Vendor extensions (Night) on real hardware.
- Themed-icon appearance on a real launcher, and the share sheet on a real device.
See `docs/TESTING.md` for the checklist.

### Known issues
- **The APK is debug-signed.** No release key is configured yet (`docs/RELEASING.md`). Android will refuse to
  update it with a release-signed build; uninstall first when that happens.
- Verdict and explanation text produced by `:core:camera` is English only; screen chrome is in string
  resources.
- detekt is the 2.0 **alpha** line, because the last stable detekt cannot parse Kotlin 2.4.
- Instrumentation tests are not run in CI (emulators on hosted runners are too slow and flaky to gate on).
- No camera permission, viewfinder, capture, sessions or export yet: those are M1–M3.

[0.1.0]: https://github.com/Mr-Dark-debug/siderea/releases/tag/v0.1.0
