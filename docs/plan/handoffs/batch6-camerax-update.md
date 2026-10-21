# Batch 6: CameraX capture update

Repo: qualified-opinion/lifetime-android.
Branch: `chore/camerax-1.5.3`, based on main `569537a` after PR #25.
Owner request: merge and continue (2026-10-21).

## Scope and compatibility
Update the shared CameraX version from 1.4.2 to 1.5.3 for the existing core, camera2, lifecycle, video and view artifacts. This is a bounded stable 1.5-series update, not the latest CameraX or its newer unified camera stack.

The [official CameraX release notes](https://developer.android.com/jetpack/androidx/releases/camera) describe:
- 1.5.0-alpha05: compileSdk 35 and JSpecify nullness annotations.
- 1.5.0-beta01: consuming Kotlin 2.0 libraries requires KGP 2.0 or newer.
- 1.5.0-rc01: minimum SDK increases to 23.
- 1.5.1: target-rotation preservation and PreviewView Activity leak fixes.
- 1.5.3: ExifInterface 1.4.2 for padded JPEG parsing, already explicitly selected by Lifetime.

Lifetime's compileSdk 35, minSdk 26 and Kotlin 2.1.21 meet those stated requirements. Confirm actual dependency/AAR metadata and compilation in CI. Keep AGP, Kotlin, Compose, KSP, wrapper, target SDK and every other declared library version fixed.

## Existing behavior to preserve
CameraScreen uses LifecycleCameraController and PreviewView, binds one photo or video use case at a time, records to app-private files with optional audio, and stops at 60 seconds or on disposal. Preserve its Finalize handling and CameraViewModel's file/row commit before optional location enrichment. No new capture mode, permission, schema or UI behavior is introduced.

## Automated acceptance
Run the full existing suite: unit tests, debug assembly, committed Room schema check, lint and minified release assembly. CaptureExportTest exercises filing/insert failure cleanup and optional-location behavior; it does not emulate camera hardware. Record the exact PR head and workflow result. This library-only change adds no test that duplicates CameraX internals.

## Phone acceptance (pending)
Record phone model, Android version and installed build. Preserve a known-good export outside the app before testing.
- Take several rear/front photos, switching photo/video modes and rotating the phone. Check preview, saved orientation, captions, date ordering and thumbnails.
- Record rear/front clips with microphone permission granted, then with it denied. Check playback/audio and confirm a denied microphone still permits silent video.
- Record to the 60-second limit; confirm one playable moment. Background during another recording, reopen, and confirm usable output is filed once without an extra broken moment.
- Reopen the camera repeatedly, including with app lock enabled. Check preview recovery, camera release and mode/camera switching.
- With optional location on, check coordinates and exported media. With it off, confirm capture still saves promptly.
- Recheck import/export/restore of a captured photo and video. Low-storage and interruption fault results remain pending unless actually reproduced.

JVM tests and builds do not establish camera/codec/OEM behavior. Do not publish a signed release or mark phone checks complete from CI.

## Handoff
Open a separate draft PR for independent review after green CI. Keep the broad #22 toolchain/runtime proposal separate.
