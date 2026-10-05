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
| Real-device fixture | lens catalogue, limits, verdicts and zoom labels computed from the **real Pixel 10 report** |
| Colour maths | blackbody chromaticity vs published CIE values, gain monotonicity, white-preserving matrix, tint direction |
| Request planning | clamping, 16 s frame duration, preview exposure cap, focus/WB decisions, tap-to-sensor mapping |
| Viewfinder analysis | histogram, clipping, focus peaking, zebras; software auto-exposure convergence |
| Orientation | JPEG/EXIF orientation formula and the horizon level |

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
  lists the device, a photo is taken and appears in the gallery, **Copy as JSON** produces a parsable report on the clipboard, red mode survives activity
  recreation (real DataStore on disk), reset restores defaults, Settings reaches the Inspector and licences.
* `CameraEngineInstrumentedTest`: the real Camera2 engine against the target's cameras: opens every lens, live
  metadata, a manual exposure confirmed in the sensor's own `CaptureResult`, a valid JPEG, a long exposure, DNG
  files (skipped per lens when the HAL's metadata is too sparse for `DngCreator`), lens switching and close.
* `TimelapseServiceInstrumentedTest`: the real foreground service against the emulator's camera: documented
  folder layout, actual per-frame values in `session.json`, clean stop and clean finish.
* `TimelapseFlowTest`: through the real UI: the calculator is shown first, pre-flight can be cancelled, a
  session runs, stops and its detail screen opens.
* `VideoExportInstrumentedTest`: real MP4s from the phone's own encoder, read back with `MediaExtractor`: one
  sample per frame, size, even timeline, crop, HEVC (skipped when there is no encoder), deflicker, unreadable
  frames, cancel, TIFF, MediaStore publishing.
* `AstroFlowTest`: through the real UI: the Astro panel, a short Astro session, star trails from it (JPEG and
  TIFF written), and ten dark frames captured and filed with the session.
* `ProcessingBenchmarkTest`: times decode, star detection, alignment, warped and direct stacking, trails and the
  16-bit TIFF on large frames and logs them (`adb logcat -s SideriaBench`). Run it on a real phone and send the
  line.
* `LongExposureFlowTest`: through the real UI: Long exposure mode, a bulb run until stopped, then Add light, Keep
  brightest and Average from the session screen (JPEG and TIFF written for each).
* `AccessibilityTest`: the Accessibility Test Framework over the camera screen, every mode panel, each manual-control
  panel and Settings.
* `CameraCapabilityReaderInstrumentedTest`: the real reader against whatever cameras the target has;
  consistency checks (unique ids, physical cameras point back to a logical parent, ranges agree with
  capabilities, one `1x` lens per facing, API 36 keys only on API 36, verdicts never over-claim, JSON
  round-trip on device).

The UI classes (`SideriaSmokeTest`, `TimelapseFlowTest`, `AstroFlowTest`, `LongExposureFlowTest`) use a `RetryRule`
that runs a failed test once more. The emulator's software GPU slows down over a long run until Compose reports
"not idle", which fails tests that pass alone and on a freshly booted emulator; every failed attempt is still
printed. Cold-boot the emulator and set the animation scales to 0 before a full run (about 25 minutes).

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

Run on the Pixel 10 (or any phone with several lenses). Tick off and report anything that fails.

**Lenses and streams**
- [ ] The lens chips show exactly the lenses you can see in the stock camera (Pixel 10: 0.6x, 1x, 5x back; 0.9x,
      1x front) and each switch opens in under ~1 s.
- [ ] For 0.6x and 5x, the image really is that lens, not a crop of the main camera. If Siderea shows a
      "zooms the main camera instead" message, note it and attach the Inspector JSON.
- [ ] RAW on 0.6x and 5x opens in Lightroom/Darktable/Photos with the right field of view and colour.

**Exposure**
- [ ] Manual shutter 1/100 + ISO 400: the viewfinder brightness is stable and the saved EXIF exposure equals the
      requested value (check Pixel's file info or `exiftool`).
- [ ] A **16 s** exposure at ISO 800 on the 1x lens: the viewfinder stays alive (with the "previews at a shorter
      exposure" hint), the progress bar counts to 16 s, the file appears, the stop button aborts cleanly.
- [ ] The saved long exposure is *brighter* than the live preview looked without night view (the preview is
      deliberately conservative); with night view on the preview approaches it.
- [ ] Shutter priority (shutter manual, ISO auto) and ISO priority settle within ~2 s and do not hunt.

**Focus**
- [ ] Autofocus + tap-to-focus: the reticle appears where you tap and the focus follows it (watch peaking).
- [ ] Manual focus: the ∞ end of the dial is truly infinity on a distant light; the near end of the dial reaches a
      sensible close distance. Report the nearest sharp distance you measure (Siderea assumes 10 cm).
- [ ] The 0.6x lens shows "fixed-focus" in the focus panel and the control is disabled.

**White balance**
- [ ] Kelvin 2800K under a tungsten bulb and 5500K outdoors both look neutral to the eye, and tint ±20 moves
      green↔magenta in the right direction. Compare with the stock camera on a grey card.
- [ ] WB presets (DAY, CLOUD, TUNGSTEN, FLUOR, SHADE) change the colour and none is garish.

**Aids and controls**
- [ ] Histogram follows the scene; clipped ends turn red when >1 % of pixels clip.
- [ ] Focus peaking highlights sharp edges and ignores blur; zebras appear on blown highlights.
- [ ] Level line is amber within 1° on a tripod; pointing at the sky shows ALT instead of the line.
- [ ] Night view brightens the viewfinder only (the photo is unchanged).
- [ ] Volume keys and a Bluetooth selfie remote take one photo each (holding the key does not burst); self-timer
      2/5/10 s counts down and can be cancelled with the shutter.
- [ ] Photos taken with the phone held sideways are saved the right way up, JPEG and DNG.
- [ ] Take another app's camera (open the stock camera): Siderea says so in plain language and recovers with
      **Try again** once the other app is closed.

## M2 · Timelapse / sessions (v0.3.0)

- [ ] (emulator) kill mid-session, relaunch, **Resume** continues numbering; (emulator) `dumpsys deviceidle
      force-idle` with the screen off keeps frames coming. Both passed on the API 36 emulator.
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
- [ ] AV1 is offered only on phones with a hardware AV1 encoder (and not at all on a phone without one).
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

- [ ] Sunset-to-night run with RAMP EXPOSURE on (JPEG, manual-exposure lens): `session.json` shows shutter
      lengthening first and ISO rising only after, with no step larger than a quarter of a stop between frames;
      the video (with deflicker off) shows no visible brightness jumps. Repeat with a sunrise.
- [ ] Ramp on a RAW-only session or a lens without manual exposure: the session says it fell back to per-frame
      metering (emulator-verified; confirm on the phone).
- [ ] TalkBack can operate every screen, including the ruler dials (swipe up / down to change a value). The
      automated Accessibility Test Framework checks pass; this is the by-hand half.
- [ ] Text at 200 % system font size: panels scroll and nothing important is clipped. (The camera readout rows stop
      growing at 1.25x on purpose.) Checked by eye at 1.6x on an emulator only.
- [ ] Red mode stays legible at every screen and dialog.
- [ ] A fresh install, first launch to first photo, takes under a minute and asks for nothing but the camera.
