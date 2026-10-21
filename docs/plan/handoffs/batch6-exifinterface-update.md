# Batch 6: isolated ExifInterface update

Repo: qualified-opinion/lifetime-android, from main `e893414` (PR #19 merged).
Branch: `chore/exifinterface-1.4.2`.
Owner request: Jan asked to continue after the CI Actions refresh on 2026-10-21.
Builder: Codex. Keep the PR unmerged for independent review and Jan's OK.

## Goal and scope
Update only the declared ExifInterface version from 1.3.7 to 1.4.2. This is the first isolated runtime-library update selected by the dependency triage report; the grouped Android/toolchain upgrade in #14 remains separate.

[Android's official release notes](https://developer.android.com/jetpack/androidx/releases/exifinterface#1.4.2) identify a fix in 1.4.2 for valid JPEGs with extra 0xFF fill bytes before markers. Lifetime depends on this reader for imported photo dates and GPS; its existing GPS writes also use the library.

Keep AGP 8.10.1, Kotlin 2.1.21, KSP, Gradle wrapper, other declared library versions, minSdk 26 and compile/targetSdk 35 unchanged. There is no app-code, Room/schema, permission, UI or backup-rule change.

## Regression coverage
Extend DocumentImportTest with a real JPEG containing EXIF date, timezone offset and GPS. After writing the tags, insert two JPEG fill bytes immediately before the EXIF APP1 marker. Import through the fake document provider and verify the known date/GPS and byte-for-byte preservation, just as for the ordinary original.

The existing test configuration runs this case on APIs 28 and 33. Ordinary import, original-access fallback, capture/export, restore round-trip and content-aware duplicate tests continue to run.

## Automated acceptance
- testDebugUnitTest and assembleDebug pass on the current toolchain. Android's AAR compatibility checks must accept this dependency at the existing SDK/AGP settings; do not raise SDK levels to make the upgrade pass.
- Committed Room schema check remains clean.
- lintDebug and minified assembleRelease pass.
- Inspect the version-catalog diff to confirm only ExifInterface's declared version changed.
- No extra tests that merely repeat the version constant. The padded-marker case exercises the actual parser and application import path.

## Phone retest (pending)
On the new branch build, import a known geotagged original and confirm its date and coordinates. Capture a new photo with location enabled and verify its location; export both and check the photos open normally and retain metadata. Do not claim these checks from JVM results.

## Out of scope
AGP 9 / built-in Kotlin migration, Room or other runtime upgrades, changing capture/import behavior, new formats in the UI, new permissions, signing or publishing a release.
