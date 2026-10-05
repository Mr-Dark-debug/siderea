# Siderea 1.1.2 validation

Checked on 2026-10-05 with JDK 21, the Android API 36 emulator and the project's pinned dependencies.
This is emulator evidence. Real camera hardware, RAW decoders, OEM battery policies and OEM installers
still need real-phone testing.

## Source and build

`gradlew qualityCheck testDebugUnitTest :app:lint :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest`
passed. ktlint and detekt reported no violations. All 311 host tests passed. Android lint had no errors;
existing warnings remain. R8 and resource shrinking produced the optimized release APK.

New host tests cover local-day grouping across Berlin midnight, a leap-year month, empty calendars,
stable version selection, exact APK assets, URL/size limits, required SHA-256 metadata and tampered bytes.

## Device checks

The gallery passes date filtering, JPEG details, swiping, real Android back navigation and accessibility
checks at normal size and at 320 dp width. A historical private-session fixture uses its authoritative
journal timestamp; Android's asynchronous MediaStore scan can temporarily use the image's added date.
The gallery observes media changes to refresh after scanning or publication.

Update integrity tests reject and clean corrupted pending files, remove interrupted/obsolete installer
files and reject an installed APK being offered as its own upgrade.

All 26 focused device tests passed on the camera/gallery revision: GalleryFlowTest, UpdateIntegrityTest, AccessibilityTest,
SideriaSmokeTest, TimelapseFlowTest and LongExposureFlowTest. These include a photo appearing while
the gallery is open, manual camera controls, all mode panels, settings, real photo capture, session
recording, H.264/ZIP exports and three Bulb combining methods. Three gallery checks also passed at 320 dp.

The final patch changes the signing configuration, version and photo-size formatter. Host checks were
rerun for v1.1.2; the published APK was then tested through Android's real installer and its UI.

## Published APK and replacement

[Stable release](https://github.com/Mr-Dark-debug/siderea/releases/tag/v1.1.2):
`siderea-v1.1.2-debug-signed.apk`, 3,660,882 bytes, version code 10102. Its SHA-256 is
`8961c90d6513a6bc743e6ab1851be8936fbd99ef1060d1d4a02de3afc3f1ac0c`.
The public GitHub digest, checksum file, downloaded APK and app-downloaded installer matched.
The APK certificate matched the persistent certificate below.

A disposable API 36 emulator started with a same-key 1.0.99 fixture. A real photo, a four-frame timelapse
and a changed haptic preference were created before the upgrade. Resetting the fixture's last-check time
simulated an elapsed check interval; on launch the app found the public v1.1.2 release, automatically
downloaded it over the emulator's unmetered connection, verified it and displayed Update ready.

The in-app Install action opened Android's Allow from this source gate. After allowing the source,
Install opened Android's confirmation showing Update. Approving it produced App installed, and the
published app reopened as 1.1.2 (10102). All ten private session files, the shared JPEG and the preferences
file remained byte-for-byte identical to their before-upgrade copies. Pending metadata and both installer
files were cleaned. A new photo captured successfully, the gallery still showed the retained images,
details correctly displayed the 58,945-byte image as 59 kB, and a manual check reported You're up to date.

A separate valid 1.1.0 APK signed with an unrelated test certificate, with matching size/checksum metadata,
was rejected and removed from pending storage without changing the installed app. The initial public
v1.1.0 APK was also automatically downloaded and explicitly rejected for its different signing key.

GitHub CI passed revisions f95554d and 1b1a792. GitHub's subsequent release runners stayed queued, so
v1.1.2 was published directly from the locally verified optimized build. The final source
[CI run](https://github.com/Mr-Dark-debug/siderea/actions/runs/37364037555) remained queued at completion;
no passing result is claimed for that run. The cancelled release runs did not publish v1.1.1 or v1.1.2.

## Signing

The locally verified release certificate SHA-256 is
`f11e976967911c8e585dd88817d6587076a802840699eebf7e3c8304bedbe3b5`.
The same persistent debug keystore is configured in the repository's Actions secret. The public v1.0.0
and initial v1.1.0 certificates differ: see [the migration instructions](UPDATES.md). The initial v1.1.0
published-APK check found that CI had not used the persistent key; its automatic download was rejected.
v1.1.2 sets the exact key path and verifies the built certificate before publication.
A lower-version same-key fixture
cannot demonstrate compatibility with that old public APK.

Screenshots in README come from the actual emulator UI, without replacement photography or mock screens.
