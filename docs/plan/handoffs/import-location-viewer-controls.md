# Follow-up: original photo location and recoverable viewer controls

Repo: qualified-opinion/lifetime-android, from main `284c719`.
Branch: `fix/import-location-viewer-controls`.
Owner authorization: Jan asked to fix both defects first on 2026-10-17, before batches 3/4.
Builder: Codex. Independent review by Claude Code or Grok Build and Jan's OK are still required before merge.

## Report and cause
- Jan reports batch 1 and ordinary import/export work, dates are correct, and camera GPS works. Gallery photo imports have no location. Device model/Android version/build are not yet supplied.
- The visual photo picker may redact GPS even with ACCESS_MEDIA_LOCATION. `setRequireOriginal` cannot reliably undo picker redaction. A gallery may also store location outside the original file, which Lifetime cannot reconstruct.
- `ZoomableImage` starts `pointerInput(Unit)` with an onTap callback capturing the initial toolbar state. Recomposition updates the argument but not that running coroutine's callback, so later taps keep setting the toolbar hidden.

## Scope
1. Use ACTION_OPEN_DOCUMENT for local photos/videos, preserving selected document URIs and existing read grants. No broad storage permission, no network, no database migration. The existing optional media-location permission request remains; denial still imports.
2. Read document streams directly rather than converting document URIs to MediaStore. Retain optional require-original/fallback behavior for direct MediaStore sources.
3. Use updated tap/zoom callbacks in photo pointer-input handlers. Restore toolbar visibility on a change of the displayed moment.
4. Add Robolectric and Compose JVM test dependencies (test/debug only) so CI exercises the actual gesture and Android document/EXIF paths. No production runtime dependency is added.
5. Record Jan's results accurately in the roadmap. Do not mark unspecified interruption scenarios as tested.

## Acceptance and validation
- Repeated single taps hide and restore photo controls after recomposition; double-tap zoom does not toggle the toolbar and invokes the current zoom callback.
- Picker requests original local image/video documents on API 28 and 33, supports multiple items, and cancelling imports nothing.
- A fake document provider serves a real JPEG with EXIF coordinates/date; repository import preserves coordinates, capture date and bytes and never modifies the source. This tests the read/copy/metadata path, not a real phone's provider.
- Existing JVM tests and debug APK build pass: `./gradlew --no-daemon testDebugUnitTest assembleDebug`.
- On Jan's phone: re-import a known original from local DCIM/Camera, check Details and exported GPS, then test repeated taps, zoom, and photo/video swiping. Prior redacted imports are not retroactively repaired.

## Limits and references
- GPS retention still requires the selected file/provider to supply embedded GPS. Select the original file, not a previously stripped copy. No metadata is guessed from the phone's current location.
- Local execution was blocked downloading Gradle (`Network is unreachable`); GitHub Actions will supply compilation/test evidence.
- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files): user-selected document access without whole-library access.
- [MediaStore](https://developer.android.com/reference/android/provider/MediaStore): picker URI limitations and original-media access.
- Scope excludes batch 3/4, signing, merging, and manual release/tag creation. Existing push-triggered debug publication is unchanged.
