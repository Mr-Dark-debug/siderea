# Contributing to Siderea

Thanks for helping. Siderea is a manual camera, timelapse and astro app for Android, built capability-first:
the UI reflects what each phone's cameras really report, so **the single most valuable contribution is a
Capability Inspector report from a phone that isn't mine**.

## Send a device report

1. Install the latest APK from [Releases](https://github.com/Mr-Dark-debug/siderea/releases).
2. Open **Capability Inspector** → **Share report** (or **Copy as JSON**).
3. Open a *Device report* issue and attach the file.

## Build

Requirements: **JDK 21**, Android SDK with platform **android-37.0**, and an emulator or phone for
instrumentation tests. No other tools; the Gradle wrapper is included.

```bash
./gradlew assembleDebug            # app/build/outputs/apk/debug
./gradlew qualityCheck             # ktlint + detekt, zero findings tolerated
./gradlew testDebugUnitTest        # host unit tests
./gradlew lint                     # Android lint, errors fail
./gradlew :app:connectedDebugAndroidTest   # needs a device/emulator
```

Before opening a pull request run `./gradlew ktlintFormat qualityCheck testDebugUnitTest lint`.
CI runs the same plus `assembleDebug` and `assembleRelease` (R8).

## Ground rules

* **No fake features.** If something only works in theory, say so in the PR and in `CHANGELOG.md` under
  *Untested on a real device*. UI for a mode that doesn't exist must say it doesn't exist yet.
* **Capability-driven.** Never hardcode a shutter, ISO, focus or size range. Read it from
  `CameraCharacteristics` and add the rule to `FeatureVerdicts` with a unit test.
* **API-level discipline.** minSdk is 29. A platform key newer than that goes behind an `SDK_INT` check inside a
  `@RequiresApi` helper. Lint will fail the build if you forget.
* **Pure logic gets unit tests** (formatting, validation, estimates, maths). Device-only behaviour gets a line in
  `docs/TESTING.md`.
* **Licensing.** Siderea is Apache-2.0. **Do not copy code from GPL apps** (Open Camera, FreeDcam and similar),
  not even small pieces. Code from `android/camera-samples` (Apache-2.0) may be used with attribution.
* **Accessibility.** Content descriptions on every icon-only control, 48 dp minimum touch targets, text in `sp`,
  contrast kept above WCAG AA in both palettes (`PaletteContrastTest` enforces the palettes).
* **Human error messages.** Say what happened and what to do next.

## Commits and PRs

Small, focused commits with an imperative subject line. Describe *why* in the body when it isn't obvious.
Link the issue. Screenshots for UI changes, in both the default and red palettes.
