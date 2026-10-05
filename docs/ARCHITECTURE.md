# Architecture

Status: describes the code at **v0.1.0 (M0)** and the decisions already taken for later milestones. Where a
decision is made on reasoning only and still has to be validated by measurement, it says so.

## Modules

```
:app                  single activity, navigation, Hilt wiring, screens (home, inspector, settings)
:core:ui              design system: theme, tokens, components (Compose, Material 3)
:core:camera          Camera2 engine; today: the capability reader and everything derived from it
:core:capture         sessions, intervalometer, foreground service        (empty until M2)
:core:processing      stacking, star trails, alignment                    (empty until M4)
:core:export          video / image export                                (empty until M3)
:core:data            settings (DataStore); sessions later
```

Dependency rule: `:app` depends on the `:core` modules, `:core` modules never depend on `:app`, and only
`:core:ui` knows about Compose. `:core:camera`, `:core:data` and the future `:core:capture` /
`:core:processing` / `:core:export` are plain Kotlin + Android framework, so the foreground service and
WorkManager workers can use them without dragging in any UI.

**Hilt lives in `:app` only.** The core modules have plain constructors and `:app/di/AppModule.kt` wires them.
That keeps them testable with fakes and avoids running Dagger's processor in every library.

## State flow

Unidirectional, per screen:

```
UI  --intent-->  ViewModel  --(suspend / Flow)-->  repository / engine
UI  <--state---  ViewModel  <--------------------  repository / engine
UI  <--effect--  ViewModel      (one-shot: clipboard, share sheet; a Channel, never state)
```

The Inspector is the reference implementation: `InspectorState`, `InspectorIntent`, `InspectorEffect`.
Composables are split into a thin stateful entry point (Hilt ViewModel) and a stateless `*Content` function
that takes state and callbacks, so each screen can be driven from a test or preview with fabricated state.

## Capability-driven design

Nothing in the app hardcodes a shutter, ISO, focus or size range. `CameraCapabilityReader` enumerates every
camera (logical, physical sub-cameras, extensions), `CharacteristicsMapper` turns each
`CameraCharacteristics` into a serialisable `CameraInfo`, and `FeatureVerdicts` derives what Siderea can do
on that lens (manual exposure, long exposure, RAW, manual focus, Kelvin + tint, hybrid AE, full-resolution
mode, vendor Night) with a plain-language explanation and next step whenever the answer is not "yes".

The same verdicts will drive the camera UI in M1: a control the lens cannot support is hidden or disabled
with the verdict's explanation, never silently failing.

Details worth knowing:

* Camera2 constants are named by reflection over the platform's own public `CameraMetadata` fields
  (`EnumNames`). A mode introduced in a newer Android release is named correctly even if the app was compiled
  against an older SDK, and there is no hand-written table to get wrong.
* Keys newer than minSdk (zoom ratio API 30, max-resolution maps and extensions API 31, **colour-correction
  mode list, CCT range and AE priority modes API 36**) are read behind `SDK_INT` checks in `@RequiresApi`
  helpers. Android lint enforces this; it caught an unguarded API 36 key that would have crashed the Inspector
  on every phone older than Android 16.
* Reading characteristics needs no permission. The `CAMERA` permission arrives with M1.
* A camera that fails to read becomes a `CameraInfo` with `error` set; it never takes the whole report down.

## Decisions

### Camera2, not CameraX (fixed by the brief)
Direct control of `SENSOR_EXPOSURE_TIME`, `SENSOR_SENSITIVITY`, `SENSOR_FRAME_DURATION`,
`LENS_FOCUS_DISTANCE`, AE / AF / AWB modes, `RAW_SENSOR` + `DngCreator`, and per-frame `CaptureResult`.
All camera work will run on a dedicated `HandlerThread` (M1), never the main thread.

### Targets
`minSdk 29`, `compileSdk` and `targetSdk 37` (Android 17, the latest stable platform installed and
supported by AGP 9.4). Android 16 (API 36) features are gated by both `SDK_INT >= 36` and by what each
camera's HAL reports, because a phone can run Android 16 while its camera HAL predates the new keys.

### Video export: `MediaCodec` + `MediaMuxer` (not Media3 Transformer)

*Decided on architecture grounds and implemented in M3 (see "Export pipeline" below). The throughput benchmark on real hardware is still outstanding; exit criteria below.*

The export has to do things a generic transcoder pipeline does not model:

1. **Real timing.** Frames are placed using the sensor timestamps recorded in `session.json`, so thermal
   drift in a long session is preserved or deliberately normalised, rather than assuming a constant interval.
2. **Per-frame pixel work that depends on other frames.** Deflicker needs a luminance pre-pass over the whole
   sequence; frame blending / motion blur needs several source frames per output frame; star-trail and
   comet-tail renders accumulate across frames.
3. **Decoding huge stills cheaply.** Sources are 12 MP+ JPEG/DNG; the decoder must sample down on load and
   stream tile by tile, not hold bitmaps.
4. **Encoder choice from capability.** Codec list and bitrate limits come from `MediaCodecList`; AV1 is
   offered only when a hardware encoder exists.

Media3 Transformer is excellent for editing existing video and for simple image-to-video, but each of the
above would be implemented as a custom effect or custom asset loader anyway, at which point it adds a large
dependency and its own threading and lifecycle model on top of the work we need to control. Driving
`MediaCodec` with an input `Surface` and writing with `MediaMuxer` keeps one pipeline we fully own and can
instrument.

*Reconsider if the M3 benchmark shows:* (a) encoder throughput on a reference device below real-time for 4K
HEVC at 30 fps from 12 MP stills, caused by our code rather than the hardware encoder, or (b) Transformer
reaches parity on features 1–3 with less code.

### Image processing: decision deferred, deliberately
The brief says "measure, don't guess", so no engine is chosen at M0. Plan for M4: implement the stacking
core behind an interface (`FrameAccumulator`: add frame, finish), build a tile-streaming Kotlin reference
implementation with unit tests on synthetic frames, then benchmark it on a real device against a C++/NDK
implementation of the same interface on 100 × 12 MP frames. A GPU path (OpenGL ES / AGSL) is evaluated for
the preview and for export-time per-frame operations. The winner is documented here with numbers.
The NDK (r27+) is installed in the dev environment, but no native code exists yet.

### Sessions and `session.json` (M2)
Every capture is a session folder in app-specific storage; export to MediaStore is on demand. Source frames
are never deleted automatically. `session.json` records requested settings plus, for every frame, the
*actual* `CaptureResult` values (exposure, ISO, frame duration, focus distance, CCT, sensor timestamp,
orientation, optional GPS, capture errors). It is written continuously so a killed process can offer
"resume" or "finalise what was captured". `kotlinx.serialization` is already in place and covered by the
capability-report round-trip tests.

**Journal.** `SessionHandle.appendFrame` writes one JSON line to `frames.jsonl` *before* anything else, so a
frame survives a kill; `session.json` is rewritten atomically (temp file + rename) and the two are merged on
load, ignoring a truncated last line. `SessionStore.interrupted()` lists sessions still marked running that no
live run owns, which is what drives the Resume / Finalize prompt.

### Timelapse runner and service (M2)
`TimelapseRunner` (in `:core:capture`, plain Kotlin, unit-tested with a fake clock) owns the schedule: an
absolute timeline that re-anchors on overrun, `GuardPolicy` decisions between frames, and the measured-overhead
tracker. It talks to the camera only through `FrameCapturer`. In `:app`, `TimelapseService` (foreground
service, type camera) holds a partial wake lock and a headless preview surface and drives the runner;
`AndroidRunnerClock` waits with `AlarmManager` allow-while-idle alarms for waits of 20 s or more so Doze can't
hold a frame back. The UI observes `CaptureSessionState`; it never owns the run.

### Export pipeline (M3)
`TimelapseVideoExporter` (in `:core:export`) plans, optionally measures, then renders:
1. **Plan:** probe the first readable frame's upright size, cut the crop (`ExportMath.cropRect`), scale to the
   chosen long side (never up), align to the encoder's width/height alignment, shrink in even steps until
   `MediaCodecInfo.VideoCapabilities` accepts size and rate. A hardware encoder is preferred; AV1 requires one.
2. **Measure** (deflicker only): decode each frame at about 240 px, take the mean Rec. 709 luma, then run
   `Deflicker.gains`.
3. **Render:** each frame is decoded with EXIF rotation applied, uploaded as a GL texture and drawn through an
   EGL window surface onto the encoder's input surface with `eglPresentationTimeANDROID` set to
   `index / fps`. A canvas was tried first and rejected: it stamps frames with the wall clock and the encoder
   then drops frames to hit its target rate. A separate drain thread feeds `MediaMuxer` so a slow encoder
   cannot block rendering.
4. **App side:** `ExportCoordinator` (singleton, its own scope) owns the work and exposes `ExportState`;
   `ExportService` (foreground, data sync) only keeps the process alive and shows progress. Finished files are
   published through MediaStore on request.

### Astro processing (M4)
`:core:processing` is plain Kotlin on plain arrays (`RgbImage`), with Android only at the edges
(`FileFrameSource` decodes JPEGs, `RgbImageIo` writes JPEG and TIFF). Everything is expressed through
`FrameAccumulator { add(frame, transform?); toRgbImage() }`, so a stack never holds more than one frame and the
accumulator. `StarDetector` -> `Alignment.estimate` (pair-hypothesis search + least squares) ->
`StackAccumulator` (bilinear warp, float mean, 16-bit TIFF). `TrailAccumulator` is a lighten blend with an optional
per-frame fade. `MasterDark` averages the dark frames and subtracts them in place. The decision recorded in the
M0 notes was to benchmark Kotlin against C++/NDK and a GPU path before choosing: only the Kotlin reference exists
and is timed (`ProcessingBenchmarkTest`); no alternative was built, so the choice is "Kotlin, because it is correct
and simple and the interface allows replacement", not "Kotlin, because it won".

### Settings
Jetpack DataStore (Preferences) behind `SettingsRepository`. Host unit tests use an in-memory `DataStore`
because DataStore's file replacement uses `File.renameTo`, which fails on Windows hosts when the target
exists; real on-disk persistence is covered by an emulator test (`redModeToggleSurvivesActivityRecreation`).

### Build
* AGP 9.4 with built-in Kotlin, Kotlin 2.4, Compose compiler plugin, KSP for Hilt.
* Convention plugins in `build-logic/` (`siderea.android.application|library|compose|hilt|licenses`,
  `siderea.kotlin.serialization`, `siderea.quality`) so platform levels and quality gates exist in one place.
  Plugin versions live only in `gradle/libs.versions.toml`.
* **detekt 2.0.0-alpha.6 (`dev.detekt`)**: the last stable detekt (1.23.8) is built against Kotlin 2.0 and
  does not run on Kotlin 2.4. This is a deliberate trade: an alpha linter, zero tolerated findings.
  ktlint 1.8 runs through ktlint-gradle with the `ktlint_official` style.
* Release builds are minified and resource-shrunk by R8; CI builds the release variant on every push so a
  shrinker regression fails CI, not a user.
* Open-source licences are generated at build time (AboutLibraries) from the resolved dependency graph.

## The photo engine (M1)

```
CaptureSettings  --RequestPlanner-->  RequestPlan  --RequestApplier-->  CaptureRequest.Builder
 (user intent)    (clamps, preview     (Android-free)                    (the only place keys are set)
                   cap, WB decision)
```

* **One thread, one open camera.** `CameraEngine` owns a `HandlerThread`. The main thread calls suspend
  functions and reads `StateFlow`s. Open, close and "camera taken by another app" are modelled as state, with
  human-readable messages mapped from Camera2's error codes.
* **Planning is pure.** `RequestPlanner` turns settings into a `RequestPlan`: values clamped to the lens, frame
  duration chosen so a 16 s shutter is legal, preview exposure capped, white balance resolved. It is unit tested
  against the real Pixel 10 report. `RequestApplier` is the only code that touches `CaptureRequest` keys.
* **Lenses.** `LensCatalog` builds the lens list from the capability report. A logical camera's default lens
  opens as the logical camera (the most compatible path); every other lens is a **physical stream**
  (`OutputConfiguration.setPhysicalCameraId`), with sensor settings also set per physical camera via
  `setPhysicalCameraKey` when the HAL lists those keys. If the phone refuses the physical streams, the engine
  falls back to the logical camera with `CONTROL_ZOOM_RATIO` and says so.
* **Streams.** Preview (a `SurfaceTexture`), plus a JPEG reader and/or a `RAW_SENSOR` reader depending on the
  chosen format. That is three streams at most, inside what a FULL-level camera guarantees. Analysis frames
  come from `TextureView.getBitmap` on a small bitmap rather than a fourth stream, which a FULL camera need not
  support alongside RAW.
* **Long exposures.** A manual shot longer than half a second pauses the repeating preview, captures, and
  restarts it. While the user has a 16 s shutter selected the preview runs at a capped shutter with raised ISO
  (`PreviewExposure`) and any shortfall is made up by a *display-only* gain, so the viewfinder stays alive and
  the saved photo still uses the exact exposure. Night view uses the same display gain.
* **Results over requests.** Live readouts and the saved facts come from `CaptureResult`, not from what was
  asked for, because the sensor rounds and the HAL overrides.
* **DNG.** `DngCreator` is fed the physical camera's result when the lens is a physical stream. Some HALs return
  results too sparse for it; that fails with a clear message and still keeps the JPEG.

### White balance without Android 16 CCT
Many cameras, the Pixel 10 included, accept only per-channel gains and a 3x3 matrix. `WhiteBalanceMath` picks
the illuminant (blackbody xy for the chosen Kelvin, shifted off the locus for tint), interpolates the sensor's
`SENSOR_COLOR_TRANSFORM1/2` by mired between its two calibration illuminants, and derives the gains that make
that illuminant neutral, then a matrix (Bradford-adapted to D65, normalised so white stays white). Checked
against published CIE values and hand-worked examples in `WhiteBalanceMathTest`. When a camera does offer the
Android 16 controls they are used instead.

### Software shutter and ISO priority
`SoftwareAe` runs on the viewfinder's mean luma a few times a second, damped, with a dead band, moving whichever
of shutter or ISO the user left on automatic. It is convergence-tested on a simulated scene.

### State
`CameraViewModel` holds `CameraUiState`; the histogram and masks are a separate flow so only the overlays
redraw. Preferences (last lens, settings, aids) are one JSON blob in DataStore.

## Testing
See [TESTING.md](TESTING.md): automated coverage, how to run it, and the manual real-device checklist.
