# Changelog

All notable changes are recorded here. Each milestone lists **what works**, **what has not been tried on a
real device**, and **known issues**. Format follows [Keep a Changelog](https://keepachangelog.com/);
versions follow [Semantic Versioning](https://semver.org/).

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
