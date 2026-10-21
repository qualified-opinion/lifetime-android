# Batch 6: Media3 playback update

Repo: qualified-opinion/lifetime-android.
Branch: `chore/media3-1.8.0`, based on main `fda75a8` after PR #23.
Owner request: continue with the next isolated maintenance step (2026-10-21).

## Goal and scope
Update the shared Media3 version from 1.6.1 to 1.8.0 for ExoPlayer and PlayerView. This bounded stable update includes Bluetooth resume A/V synchronization fixes, device surface workarounds and MP4 extractor improvements. It is not a claim that 1.8.0 is the latest version.

The [tagged release notes](https://github.com/androidx/media/blob/1.8.0/RELEASENOTES.md) document these changes. Version 1.7.1 contains the same code as 1.6.1; it would not advance the implementation. The [tagged constants](https://github.com/androidx/media/blob/1.8.0/constants.gradle) specify minSdk 21 and compileSdk 35, compatible with Lifetime's minSdk 26 / compileSdk 35. Actual dependency resolution and builds must still pass.

Keep AGP, Kotlin, KSP, wrapper, target SDK and all other declared dependencies fixed. Preserve the existing VideoPlayer behavior: file URI source, only the settled page plays, inactive pages pause/reset, ON_STOP pauses, and leaving composition releases the player. No new playback features, permissions, schema or settings changes.

## Validation
Run existing unit tests, debug assembly, committed Room schema check, lint and minified release assembly in the actual PR workflow. Record exact head and run in the PR description. No application behavior is rewritten, so no new unit test that merely reimplements library internals is added.

On the phone, use a backed-up local CameraX clip and an imported video:
- Play, pause, seek near the end and replay. Confirm audio/video stay in sync, including after pause/resume with Bluetooth headphones if available.
- Swipe photo/video/video pages; only the settled video should play. Swipe away and return, checking reset behavior and reachable player controls.
- Background during playback and return; confirm playback pauses and the viewer remains usable. Repeat with app lock enabled.
- Check portrait/landscape clips, thumbnails and restoration/export of a playable video.

These codec, audio route and surface checks require a device. JVM CI does not establish them. Keep phone acceptance pending until Jan records the build and results.

## Handoff
Request independent review of the exact final head after CI passes. Keep the broad grouped toolchain proposal #22 separate; no signed release is requested.
