# Testing

Siderea has three layers of testing. The first two run automatically; the third needs a real phone and is
a written checklist, because an emulator cannot answer those questions.

## 1. Host unit tests (CI on every push)

```
./gradlew testDebugUnitTest
```

Pure logic, no device:

| Area | What is covered |
|---|---|
| Formatting | shutter labels (`1/8000 s`, `1/2 s`, `30 s`), frame rates, focus distances, EV, sizes |
| Zoom labels | 35 mm-equivalent maths, main-lens selection in log space, 0.5x / 1x / 5x rounding |
| Feature verdicts | every capability rule, the 10 s astro threshold, the "use Virtual Bulb" message, Android 16 gating |
| Capability report | JSON round-trip, schema stability, unknown-key tolerance |
| Inspector layout | the exact rows a user sees and sends back |
| Home summary | main-camera choice, device naming |
| Repository | load once, refresh, failure and retry |
| Settings | defaults, persistence, reset, corrupt-file fallback (in-memory `DataStore`) |
| Design system | WCAG AA contrast of every text/background pair in both palettes |

Later milestones add: interval/overhead validation, storage and battery estimates, the exposure-ramp
algorithm, stacking maths and star alignment on synthetic frames, `session.json` round-trip.

## 2. Static analysis (CI on every push)

```
./gradlew qualityCheck lint assembleRelease
```

ktlint, detekt (zero findings tolerated), Android lint (errors fail the build) and an R8-minified release
build. Lint matters beyond style: it is what guards API-level calls, and it has already caught a crash on
phones older than Android 16 that the API 36 emulator could never have shown.

## 3. Emulator instrumentation tests (run locally)

Needs an emulator or device. They are not in CI yet: GitHub-hosted emulators are slow and flaky enough to
make red builds meaningless. Run them before every release.

```
./gradlew :app:connectedDebugAndroidTest
```

* `SideriaSmokeTest`: home shows the brand and honest mode status, cameras are read, Capability Inspector
  lists the device, **Copy as JSON** produces a parsable report on the clipboard, red mode survives activity
  recreation (real DataStore on disk), reset restores defaults, Settings reaches the Inspector and licences.
* `CameraCapabilityReaderInstrumentedTest`: the real reader against whatever cameras the target has;
  consistency checks (unique ids, physical cameras point back to a logical parent, ranges agree with
  capabilities, one `1x` lens per facing, API 36 keys only on API 36, verdicts never over-claim, JSON
  round-trip on device).

Result at v0.1.0: see [CHANGELOG.md](../CHANGELOG.md) for which API levels this was actually run on.

---

# Manual device test checklist

Run on a **physical phone**. For each item note the device model, Android version and Siderea version, and
file failures as a *Device report* issue with the Inspector's JSON attached.

## M0 · Capability Inspector (v0.1.0)

Install the APK, open **Capability Inspector**.

- [ ] The device name, Android version and security patch match **Settings → About phone**.
- [ ] Every back and front lens you know the phone has appears, with the right `0.5x / 1x / 2x / 5x` label.
- [ ] Physical sub-cameras appear under a logical camera (check against `adb shell dumpsys media.camera`).
- [ ] **Longest shutter** for the main lens is plausible (stock camera app astro mode typically uses up to
      ~16–30 s on a Pixel). Record the number: this is the key figure for Astro mode.
- [ ] **Manual shutter and ISO = YES** on the main lens. If NO, attach the JSON: the whole capture engine
      depends on this.
- [ ] **RAW = YES** on the main lens and the largest RAW size matches the sensor resolution.
- [ ] **Manual focus = YES** and the nearest focus distance is plausible (about 8–15 cm for most phones).
- [ ] On Android 16: **Kelvin and tint white balance** and **Hybrid auto-exposure** report what the camera
      HAL actually offers. On Android 10–15 they say "needs Android 16", not an error.
- [ ] **Copy as JSON** then paste into a text app: valid JSON, `"schemaVersion": 1`.
- [ ] **Share report** opens the share sheet with a `siderea-capabilities-….json` file attached.
- [ ] Open the app with another camera app running in the background: no crash; read problems (if any) are
      listed in plain language and **Refresh** recovers once the other app is closed.
- [ ] Rotate the phone and fold/unfold if foldable: no crash, state kept.
- [ ] Switch on **Red night-vision mode**: every screen is red-only, text is readable in a dark room.
- [ ] Android 13+: **Themed icons** on, the Siderea launcher icon follows the wallpaper colours.

## M1 · Photo mode (v0.2.0)

- [ ] Shutter at the sensor's longest exposure: the saved frame's EXIF/DNG exposure time equals the request
      (compare with `CaptureResult`, not the UI).
- [ ] Each lens: unsupported controls are disabled with an explanation, supported ones work.
- [ ] RAW: the DNG opens in Lightroom / Darktable / Google Photos with correct colour and orientation.
- [ ] Focus slider: ∞ snap is truly at infinity (shoot a distant light, compare with stock camera).
- [ ] Night-view boost brightens the viewfinder only; the saved frame is unchanged.
- [ ] Histogram, focus peaking, zebras follow the scene with no preview jank (≥ 60 fps UI).
- [ ] Volume key and self-timer (2 / 5 / 10 s) fire exactly once.

## M2 · Timelapse / sessions (v0.3.0)

- [ ] **Measured minimum interval** is shown and is ≥ the real time between frames in `session.json`.
- [ ] **2-hour background session** with the screen off, phone on a charger: all expected frames exist,
      notification stays, stop action works, no frame gap larger than the interval + overhead.
- [ ] Same on battery only: warnings appear at the configured battery level, session ends cleanly.
- [ ] **Thermal:** run in a warm environment (or with the phone in sun). Thermal warning appears before
      throttling; actual timestamps in `session.json` show the drift rather than hiding it.
- [ ] **Kill the app** (swipe away) mid-session and reboot: relaunch offers *Resume* / *Finalize*.
- [ ] **Storage full:** fill storage to < 200 MB and start a session: it stops cleanly, everything captured
      so far is intact and playable.
- [ ] **Camera taken by another app** mid-session (open the stock camera): Siderea reports it, keeps frames.
- [ ] **30-minute interval** across Doze (screen off, unplugged, stationary 1 h): frames still fire on time.
- [ ] Airplane-mode suggestion appears in the pre-flight checklist; gyro check flags a handheld phone.

## M3 · Export (v0.4.0)

- [ ] 1080p / 4K, 24 / 25 / 30 / 60 fps, H.264 and HEVC all play in the Photos app and in VLC.
- [ ] AV1 is offered only on phones with a hardware AV1 encoder.
- [ ] Deflicker removes visible flicker from a sunset test sequence without pumping brightness.
- [ ] Cancel mid-export leaves no partial file in `Movies/Siderea`.
- [ ] Export notification appears when the app is in the background and opens the result.

## M4 · Astro (v0.5.0)

- [ ] Dark frames: cover-the-lens guidance works; hot pixels visibly drop after subtraction.
- [ ] Stack of 100 × 12 MP frames completes without OOM on the reference phone; record time and peak memory.
- [ ] Star alignment: stacked stars are round, not trailed, on a tracked-by-hand 60-frame set.
- [ ] A bump or tap during capture is flagged as a gyro spike and that frame can be excluded.
- [ ] Star-trail output: no gaps with gap filling on; comet-tail fade looks right.

## M5 · Virtual Bulb (v0.6.0)

- [ ] Requested exposure longer than the sensor max produces a merged result of that effective length.
- [ ] Average mode smooths water / clouds; Lighten mode keeps light trails; live progressive preview updates.
- [ ] Bulb: start/stop with the live timer; stopping mid-frame keeps completed frames.

## M6 · Exposure ramp and polish (v1.0.0)

- [ ] Sunset-to-night run: exposure changes smoothly, no visible steps, bounded by the configured max step.
- [ ] TalkBack can operate every screen; text at 200 % system font size does not clip.
- [ ] Red mode stays legible at every screen and dialog.
