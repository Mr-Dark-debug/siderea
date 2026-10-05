# Siderea 1.1 design

The supplied references establish a restrained camera-body interface: black surfaces, a large live image,
compact numeric controls, one warm accent and a centred shutter. Photo starts in Auto with a clearly labelled
Pro toggle. Pro reveals the existing five adjustable readouts and ruler panels. Timer and settings stay at
the top; format, aspect and viewfinder aids move into a Tools sheet. Lens choices sit over the bottom of the
viewfinder; the lower strip offers Photo, Timelapse, Astro and Bulb. Gallery is always reachable beside the
shutter, including before the first capture. Existing camera capabilities and capture services remain intact.

Gallery reads app-owned MediaStore images and private session frames without broad storage permissions.
A timeline groups items by local capture date; a calendar marks dates with images and filters by day.
Swiping in the viewer changes photos, pinch zoom inspects detail, Info shows file and actual capture metadata,
and Share grants temporary read access. Unsupported RAW previews explain the limitation and offer Open.
Empty, loading and read-error states must remain distinguishable. Session processing/export stays accessible.

Updates query the public stable GitHub release endpoint on foreground entry, throttled to six hours.
Automatic checks can be disabled. Wi-Fi downloads automatically; mobile downloads require an explicit tap.
Download progress and retries live in Settings. A ready update offers Install or Later. Android owns the final
installation consent. An APK must match the asset size and SHA-256, installed package, increasing version code
and installed signing certificate. Download is cancellable and temporary files are cleaned. Installer files
stay private and only the selected APK is exposed through FileProvider. No uninstall is initiated by the app.

Version 1.1.0 establishes a persistent debug signing key for CI. The 1.0.0 public release was signed by a
different ephemeral CI key: replacement compatibility cannot be manufactured without that private key.
Document the one-time migration and session export requirement if that key cannot be recovered.

Validate with unit tests for date grouping and update selection/integrity policy; existing unit tests, lint,
detekt, ktlint and release shrinker; emulator gallery/camera/accessibility flows; installer and same-key
replacement checks. Public release metadata, checksum and signer are verified after publishing. Emulator
evidence is not real-camera hardware evidence.
