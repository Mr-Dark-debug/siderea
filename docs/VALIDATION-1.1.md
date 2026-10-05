# Siderea 1.1.0 validation

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

The final focused device regression and published-APK upgrade results will be recorded here after the
release workflow completes. No published-APK replacement is claimed by this pre-publication report.

## Signing

The locally verified release certificate SHA-256 is
`f11e976967911c8e585dd88817d6587076a802840699eebf7e3c8304bedbe3b5`.
The same persistent debug keystore is configured in the repository's Actions secret. The public v1.0.0
certificate differs: see [the migration instructions](UPDATES.md). A lower-version same-key fixture
cannot demonstrate compatibility with that old public APK.

Screenshots in README come from the actual emulator UI, without replacement photography or mock screens.
