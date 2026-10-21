# Batch 6: Coil image and thumbnail update

Repo: qualified-opinion/lifetime-android.
Branch: `chore/coil-3.2.0`, based on main `0056cf3` after PR #24.
Owner request: merge and continue (2026-10-21).

## Goal and scope
Update the shared Coil version from 3.1.0 to 3.2.0 for coil-compose and coil-video. The [tagged changelog](https://github.com/coil-kt/coil/blob/3.2.0/CHANGELOG.md) includes image-size cancellation and R8 warning fixes. This is a bounded stable update, not a claim to use the latest release.

Coil 3.2.0 requires Java 11 bytecode; Lifetime targets Java 17. Its [upstream dependency catalog](https://github.com/coil-kt/coil/blob/3.2.0/gradle/libs.versions.toml) uses Kotlin 2.1.20, Compose 1.8.0 and coroutines 1.10.2. Its [SDK settings](https://github.com/coil-kt/coil/blob/3.2.0/gradle.properties) use minSdk 21 and compileSdk 35. Keep Lifetime's Kotlin 2.1.21, Compose BOM 2025.05.01, coroutines 1.10.2, minSdk 26 / compileSdk 35 and current AGP/KSP/wrapper. Verify the actual resolved build in CI.

Keep LifetimeApp's shared loader and VideoFrameDecoder registration, local file requests, crossfade, viewer zoom and timeline behavior. Do not add network fetchers, dependencies, permissions or database changes.

## Validation
Run existing unit tests, debug APK assembly, committed Room schema check, lint and minified release assembly on the final PR head. Record the exact head and run in the PR description. The dependency update does not rewrite application behavior; no new test that merely duplicates Coil internals is added.

Phone acceptance remains necessary:
- Scroll a mixed photo/video timeline repeatedly, including captioned moments. Confirm thumbnails appear and remain attached to the correct moments.
- Open portrait/landscape and EXIF-rotated originals. Check orientation, pinch/double-tap zoom, toolbar toggling and swiping.
- Open a CameraX video and an imported video; verify both thumbnails and playback. Return to the timeline and repeat after backgrounding.
- Restore a small export with photos/videos; confirm the new thumbnails and original moments remain usable.

Record phone, Android version, build and per-check results. CI does not establish real video frame decoding or device rendering.

## Handoff
Open a separate draft PR for independent review after CI, preserving the broad #22 toolchain migration as separate work. No signed release is requested.
