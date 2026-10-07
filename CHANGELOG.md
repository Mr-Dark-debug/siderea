# Changelog

All notable changes are recorded here. Each milestone lists **what works**, **what has not been tried on a
real device**, and **known issues**. Format follows [Keep a Changelog](https://keepachangelog.com/);
versions follow [Semantic Versioning](https://semver.org/).

## [1.2.0] - 2026-10-07

### Added and changed
- Built-in photo editor: crop/aspect, drag crop position, zoom, rotate, mirror, brightness, contrast,
  saturation and warmth. Save copy keeps the original and capture date, writes a JPEG up to 8 MP,
  and preserves photographic EXIF where available.
- Gallery single/batch deletion with confirmation, multi-selection and sharing. Source-frame deletion
  updates the session journal without deleting a paired JPEG/RAW file. Capturing/exporting sessions are protected.
  Processed session JPEG/PNG results now appear alongside source photos.
- Simple Astro presets: Night sky, Milky Way, Star trails and Moon; actual exposure/ISO/focus summaries,
  lens-aware limits, short tripod guidance and a cancelable five-second start delay. Advanced controls
  retain frame spacing, manual exposure/focus and screen behavior. Recorded presets recommend stack/trail processing;
  Moon sessions offer averaging without star detection.
- Timelapse now offers Normal and Astro. Astro records locked long-exposure JPEG frames, calculates
  capture cadence from exposure and save overhead, supports recording durations/until stopped, and exports
  a video through the existing session workflow.
- Concise settings groups, confirmed reset, and a dedicated Updates page with installed version,
  download progress, verification status, last successful check, automatic Wi-Fi downloads and release notes.

### Limits and upgrade notes
- Presets are starting points and may need adjustment to sky brightness and lens capabilities.
  Pixel Camera's proprietary Night Sight algorithms are not included. Video is created from the session
  after recording; sky photo stacking remains a separate processing action.
- RAW files can be shared/deleted; edit their JPEG pair. Editing does not overwrite full-resolution originals.
- Same persistent signing key as v1.1.2: in-place upgrades preserve data. Older v1.0.0/initial v1.1.0
  signing-key migration still needs the one-time reinstall described in [UPDATES.md](docs/UPDATES.md).
- Real-phone, real-night-sky image quality and OEM installer testing remain outstanding.

### Verified
- 319 host tests, quality checks, Android lint and optimized APK build pass. All 41 focused emulator checks pass
  across the final camera and gallery runs, including Moon averaging and astro timelapse video export.
  Gallery/editor and accessibility checks also pass at 320 dp width.
- See [the validation report](docs/VALIDATION-1.2.md) for the published APK and in-place upgrade evidence.

## [1.1.2] - 2026-10-05

### Fixed
- Show readable, localized photo sizes in bytes, KB or MB instead of rounding small images to "0 MB".
- This is the final published camera/gallery/update release with verified persistent signing.

## [1.1.1] - 2026-10-05

### Fixed
- Explicitly configure the persistent signing-key path in Gradle and verify the built APK certificate
  against the configured keystore before publishing. The initial v1.1.0 CI build used a different key
  despite a persistent key being configured; the updater rejected it during end-to-end verification.
- v1.1.0 is marked prerelease. Use v1.1.2 for the validated camera, gallery and update release.
  If you installed the initial v1.1.0 APK, export private sessions before a one-time reinstall too.

## [1.1.0] - 2026-10-05

### Added and changed
- A simpler camera with Auto/Pro controls, a larger viewfinder, lens pills, a centred shutter and a
  Tools sheet. Format, aspect, night view, grid and focus aids remain available without crowding the screen.
- Built-in gallery for app-owned shared photos and private session JPEG/RAW frames: date headings,
  a month calendar with capture markers, day filters, swiping, pinch zoom, capture/file details and sharing.
- GitHub update checks on foreground entry, six-hour throttling, optional automatic checks, unmetered
  automatic downloads, mobile-data download buttons, progress/cancel/retry and Android-approved installation.
- APK verification: exact release asset name, HTTPS/redirect limits, byte count, SHA-256, package,
  increasing version code and matching signing certificate. Pending downloads survive restart and obsolete
  installer files are removed after replacement.
- Persistent CI signing. Releases fail if no persistent key is configured, instead of generating a new key.

### Migration and limitations
- The public v1.0.0 APK used a different ephemeral signing key. Export private sessions before a one-time
  reinstall if Android refuses replacement. Shared photos remain on disk, but the reinstalled app may need
  the system gallery to view old images whose Android ownership was removed. See [UPDATES.md](docs/UPDATES.md).
- Android requires source-install permission and final installation approval; updates cannot install silently.
- RAW previews depend on the device decoder; Open original remains available when a preview cannot be made.
- Camera hardware behavior and OEM installers still need real-phone testing. Emulator evidence is recorded
  in [the v1.1 validation report](docs/VALIDATION-1.1.md).

## [1.0.0] - 2026-10-05

**Milestone M6: exposure ramp, polish, accessibility. First stable release.** Every feature in the original brief
is built. What has **not** happened is a run on a real phone: see the list below before trusting it with a night
you cannot repeat.

### What works
- **Exposure ramp** for timelapses over changing light (a toggle next to LOCK EXPOSURE). The controller keeps one
  number, the total exposure in stops, compares each finished frame's brightness (from its own JPEG) with a
  target, and moves the total by half the error, never more than a quarter of a stop per frame and not at all
  inside a 0.1 stop dead band. The total is split shutter first (cleanest image), then ISO up to a cap (1600 by
  default); the shutter is capped at 60 % of the interval. It reports when both are at a limit, carries on from
  the last frame's exposure after an interruption, and falls back to per-frame metering with a note in the session
  if the lens has no manual exposure or the frames are RAW-only. The per-frame values land in `session.json`;
  deflicker at export finishes the job.
- **Accessibility pass:** the Android Accessibility Test Framework runs over the camera screen, all four mode
  panels, the SS / ISO / EV / WB / FOCUS panels and Settings, and finds nothing. Text at 1.6x system size was
  checked by eye: the camera's own readout rows now stay on one line (values shrink instead of wrapping, chrome
  stops growing at 1.25x), panels scroll and scale fully.
- Everything from 0.1.0 to 0.6.0: Capability Inspector; manual photo (JPEG / RAW / RAW+JPEG, histogram, peaking,
  zebras, level, night view, red mode); timelapse with service, sessions, resume, guards and pre-flight; video /
  ZIP / TIFF export with deflicker; Astro with star trails, aligned stacking and dark frames; Virtual Bulb.

### Verified
- 303 host unit tests (adds the ramp simulated against sunsets and sunrises with a pretend camera: smooth steps,
  brightness near target, shutter before ISO, limits respected and reported, dead band, bias; and old-session
  compatibility for new config fields).
- 53 emulator tests on Android 16 (adds the accessibility checks, and a service-level test that asks for a ramp on
  a camera that cannot ramp, which must say so in `session.json` and still capture). A UI test that toggled the ramp
  chip was dropped: on the emulator its result depended on how slow the software-rendered GPU had become.
- ktlint, detekt, Android lint (including the instrumentation sources) and an R8 release build are clean.

### Untested on a real device (everything below needs a phone)
- **All camera behaviour on hardware.** Siderea has only ever opened the emulator's virtual cameras. It was designed
  around a real Pixel 10 capability report, which is not the same as running on one.
- The physical-stream path for the 0.6x and 5x lenses, 16 s exposures, DNG creation from a physical camera,
  computed Kelvin / tint colour, manual focus range, software priority modes, volume keys and remotes.
- Long sessions: 2-hour background runs, Doze with 30-minute intervals, thermal behaviour, battery estimates.
- Video playback in Photos / VLC, HEVC and AV1 on hardware, 4K at 60 fps.
- Star detection and alignment on real skies over hours (synthetic fields only so far), RAW darks.
- The exposure ramp over a real sunset: the control law is simulated, not observed.
- TalkBack by hand (only the automated checks ran), and Red mode on every dialog.

### Known issues
- RAW (DNG) frames are saved but not decoded: stacking, trails, bulb combining and ramp metering use the JPEGs.
- Averaging is a plain mean (no sigma clipping), so satellites and planes survive in stacks at reduced strength.
- No GPS tagging, no localisation (English only), no live preview of a growing virtual bulb.
- Releases are signed with the debug key until the signing secrets are added (see `docs/RELEASING.md`), so updating
  from a later release-signed build needs an uninstall first.
- The emulator UI tests need a freshly booted emulator for a clean full run and retry once on a slow one.

## [0.6.0] - 2026-10-05

**Milestone M5: Virtual Bulb.** The LONG EXPOSURE mode is real: a long exposure assembled from many short ones.

### What works
- **Long exposure mode** on the mode strip. Choose a total time (30 s, 1, 2, 5, 10, 30 min, 1 h) or **BULB**
  (until you press stop). The length of each frame comes from the SS and ISO controls; the panel shows how many
  frames that makes, how long it takes on the clock, and warns when frames are so short the count would be absurd,
  or when the camera has no manual shutter at all (as on the emulator).
- **Sensible start**: entering Long exposure or Astro on a lens with manual exposure switches an automatic
  exposure to manual (5 s at the lowest ISO for a bulb, 15 s at ISO 1600 for the sky) so you start from a long
  frame rather than 1/120 s.
- **Same engine as timelapse**: foreground service, wake lock, guards, pre-flight and resume. The interval is
  the exposure plus whatever the phone needs to save the frame, never less.
- **Combine frames** from the session screen:
  - **Add light**: sums the frames in **linear light** (a plain 2.2 gamma undoes the JPEG tone curve), so ten equal
    frames look like ten times the exposure, not like ten times the encoded value. An automatic gain keeps the
    brightest 0.2 % from clipping.
  - **Keep brightest**: per-pixel maximum, for light painting and moving lights.
  - **Average**: removes anything that was only there briefly (people, traffic) and cleans up noise.
  The result is a JPEG plus a TIFF, saved to the gallery on request.

### Verified
- 291 host unit tests (adds linear-light addition against the formula, clipping vs the protecting gain, lighten
  keeping every moving light, average removing a brief intruder, mismatched and unreadable frames, cancellation,
  and the frame-count and interval maths).
- 48 emulator tests on Android 16 (adds a UI test that runs a bulb session until stopped and combines it all three
  ways, and a smoke test that every mode on the strip opens its own panel).
- ktlint, detekt, Android lint and an R8 release build are clean.

### Untested on a real device
- A real 16 s frame sequence on a Pixel: the gaps between frames, how the combined result compares with a true
  long exposure, and heat over a long run.
- Whether the plain 2.2 gamma is close enough to a phone's real tone curve; RAW (DNG) frames would be exact but are
  not decoded yet.

### Known issues
- The frames are separated by the save time (about a second), so a light moving fast can show tiny breaks in
  *Add light* and *Keep brightest*. This is inherent to stacking short exposures.
- A bulb is built from JPEG frames only.
- No live preview of the accumulating exposure while it runs; open the session afterwards.
- The exposure ramp and the final accessibility polish are still to come.

## [0.5.0] - 2026-10-05

**Milestone M4: astro.** Astro mode captures a sky session; the session screen turns it into star trails or an
aligned stack, with dark-frame subtraction.

### What works
- **Astro mode** on the mode strip: frames per session (20 to 400, or until stopped), the gap between frames, and
  a live line that says what you are about to do. The exposure comes from the normal SS and ISO controls. The
  interval is the exposure plus the gap but never less than the measured save time, so the schedule cannot overrun
  on every frame. A hint applies the 500 rule to the lens's 35 mm equivalent focal length.
- **Same service, same safety** as timelapse: foreground service, wake lock, thermal / battery / storage guards,
  pre-flight checklist, resume after interruption. Sessions are filed as `Astro`.
- **Star trails**: every pixel keeps its brightest value across the frames. **Comet trails**: older light fades.
- **Aligned stacking**: stars are detected (local maxima over the sky background with a "wings" check that keeps
  hot pixels out), the frame's star pattern is matched to a reference frame's by trying a shift-and-rotation from
  every comparable pair of stars and keeping the one that explains the most, refined by least squares; frames
  are warped with bilinear sampling and averaged in float. Frames that cannot be matched (too few stars, a
  different sky, a different size, unreadable) are **left out and reported with the reason**, never added
  misaligned. The result is written as a **16-bit TIFF** (the mean of many 8-bit frames has real sub-level
  precision) and a JPEG.
- **Dark frames**: capture 5, 10 or 20 with the lens covered at the session's exact exposure and ISO; they are
  averaged into a master dark and subtracted from every light frame before trails or stacking.
- **Brighten** applies automatic levels (black point under the sky background, gamma lift) so a dark-sky result
  is not almost black.
- **Memory-aware**: working size is estimated from the frame size and the app's heap limit, and the work runs at
  1/2, 1/3, ... size when a full-size stack would not fit, saying so in the result.
- Everything runs in plain Kotlin behind a streaming `FrameAccumulator` interface, so only one frame is held in
  memory plus the accumulator.

### Verified
- 278 host unit tests; the new ones render **synthetic star fields with known truth**: star centres to under 0.4 px,
  shift recovered to 0.3 px and rotation to 0.05 degrees, a different sky rejected, noise reduced by stacking,
  stars staying sharp where a plain average smears them, hot pixels removed by the master dark while stars
  survive, trails and comet fading, cancellation, and the 16-bit TIFF keeping a value 8 bits cannot.
- 47 emulator tests on Android 16 (adds a UI test that runs an Astro session, makes star trails, captures ten
  dark frames, and a timing check of the processing core on large frames).
- Timing on the emulator (a desktop x86 CPU, not a phone) for one 3 MP frame: decode 29 ms, star detection 73 ms,
  matching 571 ms, warped stack add 276 ms (parallel over rows; 2.97 s before that optimisation), direct add
  25 ms, trail add 49 ms, 16-bit TIFF 232 ms. Scale by about 4 for a 12 MP frame.
- ktlint, detekt, Android lint and an R8 release build are clean.

### Untested on a real device
- Everything with real stars: detection thresholds, alignment over a multi-hour sequence with field rotation,
  and the look of the result. The emulator's virtual room has no sky; its stack attempt is only checked to fail
  gracefully.
- Dark-frame subtraction on JPEG sources is an approximation (the camera has already denoised and tone-mapped);
  it is most useful for hot pixels. Real gain needs RAW darks and lights (see known issues).
- Speed and memory on 12 MP and larger frames on a phone: the benchmark test logs timings (tag `SideriaBench`)
  but the emulator's numbers are not representative, and **no NDK or GPU alternative was benchmarked**. The
  Kotlin implementation is kept because it is correct and simple, and the accumulator interface leaves room to
  swap it.
- Long runs: heat and battery over a night of 15 s frames.

### Known issues
- Processing reads the **JPEG** frames. RAW (DNG) sessions are saved but not yet decoded for stacking.
- Alignment is a similarity transform (shift, rotation); it does not correct lens distortion or atmospheric drift.
  Very wide lenses may show edge misalignment.
- Trails are not gap-filled: with a 1 s gap a bright star shows a faint break between exposures.
- Averaging is a plain mean: satellites and aeroplane trails stay in the stack at reduced brightness (no sigma
  clipping yet).
- Virtual Bulb, the exposure ramp, and accessibility polish are still to come.

## [0.4.0] - 2026-10-05

**Milestone M3: export.** Sessions can now become video, a ZIP, or a TIFF. No new capture features.

### What works
- **Video** from a session's JPEG frames with the phone's own hardware encoder (`MediaCodec` with an input
  surface, written with `MediaMuxer`): **H.264**, **HEVC**, and **AV1 only when the phone has a hardware AV1
  encoder**. Frame rate 12 / 24 / 25 / 30 / 60, output size (source / 4K / 1080p / 720p, never enlarged), crop
  (full / 16:9 / 4:3 / 1:1 / 9:16), quality (draft / good / best) and a **live estimate** of frames, size,
  duration and file size before you start.
- **Deflicker** (light / medium / strong): a luminance pre-pass over the whole sequence, then a per-frame gain
  towards the average of its neighbours, so a single bright or dark frame is pulled in but a slow sunset change
  is kept.
- **Exact timeline.** Frames are drawn through OpenGL with their own presentation times. A first version drew
  with a canvas and the encoder, seeing frames arrive far faster than the video's frame rate, silently dropped
  one; the emulator test caught it.
- **Encoder limits respected.** Size and rate are checked against `MediaCodecList`, aligned, and shrunk in even
  steps when the encoder refuses; the result says so when that happened.
- **ZIP** of the frames (JPEG and DNG stored, JSON compressed) plus `session.json`; **TIFF** of any one frame
  (tap a preview). Both are streamed, so a big frame is never held twice in memory.
- **Runs in the background:** exports live in an app-wide scope with a foreground service (data sync) and a
  progress notification, can be cancelled, delete their partial file on cancel or failure, and leave frames
  untouched. Results go to `Movies/Siderea`, `Pictures/Siderea` or `Download/Siderea` through MediaStore (no
  storage permission) or the share sheet.
- Unreadable frames (for example a half-written JPEG after a crash) are skipped and counted; frames where the
  gyro flagged movement can be left out.

### Verified
- 252 host unit tests (adds crop/size/bitrate maths, deflicker behaviour, TIFF round-trip through an independent
  parser, ZIP contents and cleanup).
- 44 emulator tests on Android 16 (adds 10 that encode real MP4s and read them back with `MediaExtractor`: one
  sample per frame, correct size, even 1 / fps spacing, crop, HEVC, deflicker measurably reducing brightness
  swing, skipped frames, cancel deleting the file, TIFF size, MediaStore publishing; and a UI test that exports a
  video and a ZIP from a real session).
- By hand on the emulator: Make video, progress, and Save to phone put the MP4 in `Movies/Siderea`.
- ktlint, detekt, Android lint and an R8 release build are clean.

### Untested on a real device
- Playback of the output in the Photos app, VLC and other players; HEVC and AV1 on real hardware.
- 4K at 60 fps and long sequences (hundreds of 12 MP+ frames): speed, memory and thermal behaviour.
- Whether a phone's hardware encoder honours the requested bitrate on timelapse material.
- The export notification and the data-sync service time limit on Android 15+.

### Known issues
- Frames are placed at a constant frame rate. The sensor timestamps in `session.json` are not used to retime
  the video, so thermal drift in a long session is neither reproduced nor normalised.
- Video is made from the **JPEG** frames only; DNG-only sessions have nothing to turn into video yet.
- TIFF is 8-bit from the JPEG; 16-bit output arrives with the stacking in M4.
- Exports run one at a time.
- Astro, Long exposure and the exposure ramp are still not built.

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
