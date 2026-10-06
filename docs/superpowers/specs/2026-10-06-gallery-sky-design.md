# Gallery tools and simple sky shooting

The user requested gallery editing/deletion, simpler Astro controls inspired by Pixel Camera, astro timelapse, and better settings/update UX. Continue autonomously through implementation and validation as authorized in the conversation.

## Approach

Keep the native Camera2/Compose architecture. Named sky presets apply real manual exposure/focus within reported lens limits; advanced controls stay available. This is preferable to adding a second camera pipeline or exposing all technical parameters on entry. Pixel's proprietary Night Sight processing is not part of Siderea.

Google's [night photography guide](https://support.google.com/pixelcamera/answer/9708795?hl=en) recommends stability, far focus, a countdown and optional astro timelapse. Its [video guide](https://support.google.com/pixelcamera/answer/7064897?hl=en) presents time lapse as a simple choice with Night Sight under More light. Adopt the interaction ideas using Siderea's existing scheduled capture and export pipeline.

## Gallery

Add Edit and Delete to the viewer, and selection, sharing and batch deletion to the grid. Confirm destructive actions. Saved edits are new JPEGs under Pictures/Siderea/Edits, retaining the source capture date and photographic EXIF where available. Originals and session frames remain unchanged by editing. Crop with aspect choices, zoom and position, rotate, mirror, brightness, contrast, saturation and warmth. Bound decoded editing images to eight megapixels to keep phone memory use predictable; show this limit in the editor. Decode and render off the UI thread. Reject RAW editing with a clear JPEG requirement.

Deletion revalidates app ownership or session membership. Session source deletion updates its journal and manifest so reopening/export cannot resurrect deleted media. Never delete a capturing or exporting session's files. Keep paired RAW/JPEG files independent. Include processed session JPEGs in the gallery as well as source frames. Viewer tracks refreshed items after deletion, including the final image.

## Sky capture

Presets: Night sky (8s/ISO1600), Milky Way (15s/ISO3200), Star trails (20s/ISO800), Moon (1/250s/ISO100). These are starting points, not guarantees of quality. Clamp exposure against shutter and frame-duration limits and ISO against sensor limits; point-star presets additionally use the approximate 500 rule. Far focus when available, otherwise autofocus with a visible indication. Show the actual applied values and a concise tripod hint. Night sky/Moon default to 20 frames, Milky Way to 40, Star trails to 30 minutes. Preset choice persists and recommends the corresponding stack/trail processing in the session page. Advanced controls include duration/frame count, frame gap, start delay and existing manual controls. Default start delay five seconds, with cancelable countdown.

Timelapse has Normal/Astro choices. Astro uses locked long exposures and sky presets, a duration choice, and cadence based on exposure plus gap/save overhead. No exposure ramp or incompatible RAW-only video input. Mark the saved session Astro timelapse; it resumes with identical configuration and exports through the existing video exporter. The UI calculator and launched schedule use the same policy.

## Settings and updates

Settings groups are Display, Library, App, with concise icon-led navigation. Updates gets a dedicated page near the top, installed version, clear checking/download/verified/permission/error states, last successful check, release link and automatic Wi-Fi download toggle. Keep APK digest, package, version and signer verification unchanged. Reset preferences requires confirmation and does not delete pictures.

## Validation and publication

Host tests cover preset clamping/cadence, persisted defaults and journal deletion. Device tests cover copy editing, pixel geometry/color, originals retained, single/batch deletion, active-session guard, preset UI, astro timelapse capture and video export, settings and updater navigation/accessibility. Run quality gates, lint, debug/release builds, existing affected device regression and compact-screen checks. Publish a same-key v1.2.0 release following existing release docs, verify downloaded APK hash/certificate and upgrade the disposable emulator from published v1.1.2. Report real-phone and real-sky testing boundaries honestly.
