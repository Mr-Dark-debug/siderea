# Gallery and sky shooting

## Gallery

Tap a thumbnail to browse, pinch to zoom, share, view details, edit or delete. **Edit → Save copy** creates
a JPEG in `Pictures/Siderea/Edits`; it keeps the capture date and EXIF camera settings and leaves the original
unchanged. Edits are rendered at up to eight megapixels/4096 pixels on the longest edge for predictable memory use.
Crop choices are Original, Square, 4:3 and 16:9. Crop zoom and dragging reposition the crop; rotation and mirroring
apply before cropping. Light and Color tabs offer brightness/contrast and saturation/warmth. RAW editing is unavailable:
use a JPEG capture/pair while keeping the DNG original.

Long press a grid tile or tap Select photos for multiple selection. Selected photos can be shared or permanently
deleted after confirmation. Deleting a source frame affects future session exports; its paired JPEG/RAW stays unless
selected too. Running or exporting sessions cannot be altered. Finalise interrupted sessions before deleting their
frames. Processed JPEG/PNG files inside session exports appear in the gallery and can be managed independently.

## Astro

Choose a preset, a capture length, settle the phone on a tripod, and press the shutter. Clear blocking items in the
checklist and tap Start. The default five-second countdown can be cancelled with the shutter. Stop saves the captured
frames. Open session, then Trails / stack to create an aligned sky photo or trails; dark frames remain optional.

| Preset | Requested starting point | Default length | Suggested processing |
| --- | --- | --- | --- |
| Night sky | 8 s, ISO 1600 | 20 frames | Aligned stack |
| Milky Way | 15 s, ISO 3200 | 40 frames | Aligned stack |
| Star trails | 20 s, ISO 800 | 30 minutes | Star trails |
| Moon | 1/250 s, ISO 100 | 20 frames | Average moon frames or single frame |

The applied shutter and ISO in the panel are authoritative. Sensor shutter/frame-duration and ISO limits may shorten
the exposure or reduce ISO; point-star presets also use the approximate 500/focal-length rule. The Moon is bright,
so its short exposure is intentional. Moon sessions offer Create moon photo, which averages frames without requiring
stars; single frames are also accessible in the gallery. Manual Far focus is used when supported;
otherwise the summary shows Auto focus. Tap Advanced for frame gap, start delay, focus and exposure/ISO controls.
The top Delay button cycles the same sky start delay (0, 3, 5 or 10 seconds). Photo Auto/Pro controls stay out
of sky mode, where presets and Advanced hold the relevant controls.

## Astro timelapse

Choose Timelapse → Astro, Night sky or Milky Way, and a recording length. The interval is the exposure plus the chosen
gap, or longer when required by measured/estimated save overhead. Exposure is locked and ramping is disabled. RAW-only
capture changes to RAW+JPEG so video has JPEG source frames. Recording creates an Astro timelapse session with actual
per-frame metadata. Stop → Open session → Make video creates the MP4, with crop, size, fps and deflicker controls.
Interrupted sessions can be resumed with their saved capture configuration. Recording until stopped is available.

Long exposures need a stable tripod and a dark sky. Hardware limitations and sky brightness still require judgment;
these presets are starting points, not a quality promise or Pixel Night Sight processing.

## Research

Google's [night photography guide](https://support.google.com/pixelcamera/answer/9708795?hl=en) describes stable placement,
far focus, start timers and optional astro time lapse. Its [video guide](https://support.google.com/pixelcamera/answer/7064897?hl=en)
places Night Sight inside Time Lapse's light controls. These informed Siderea's simple preset/start flow and Normal/Astro
choice. Siderea continues using its existing Camera2 exposure schedule and local export/stacking implementation.
