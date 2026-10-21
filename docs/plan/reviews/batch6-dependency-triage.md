# Batch 6 dependency triage and CI Actions refresh

Checked on 2026-10-21 against main `057d137`, after PR #18 merged.
Implementation branch: `chore/ci-actions-refresh`.

## Evidence

| Existing PR | Checked head | CI evidence | Decision |
|---|---|---|---|
| #11: grouped GitHub Actions | `4932198` | Build APK run 39089342743 and Signed release PR smoke run 39089342741 passed. Those checks used the older application base. | Prepare the four Action updates on current main, explicitly selecting the open-source basic cache provider. Leave #11 open until the replacement PR is reviewed and merged. |
| #14: grouped Gradle/toolchain/runtime | `7a02202` | Build APK run 39091496112 failed during plugin application in app/build.gradle.kts, before compiling the app or running tests. | Keep unmerged. The first blocker is the Kotlin/AGP migration, not a failure in restore. |

The #14 job (117569035431) reports that AGP 9 no longer needs the separate `org.jetbrains.kotlin.android` plugin. The grouped upgrade raises AGP to 9.4.1, Kotlin to 2.4.21 and the wrapper to 9.8.1 alongside many runtime libraries. Removing one plugin is not proof that the rest of this upgrade works; the job stopped at that first blocker.

## Selected maintenance change

Only workflow tooling changes:
- `actions/checkout`: v4 to v7 in both workflows.
- `actions/setup-java`: v4 to v6 in both workflows. Temurin Java 17 remains selected.
- `gradle/actions/setup-gradle`: v4 to v6 in both workflows, with `cache-provider: basic`.
- `actions/upload-artifact`: v4 to v7 in the debug build workflow, keeping the existing artifact name and path.

The official Gradle v6 documentation distinguishes its default proprietary enhanced cache from the MIT-licensed basic provider. The action metadata explicitly supports `cache-provider: basic`. Use that provider to keep caching free and open source without selecting the enhanced service.

References checked:
- [Gradle Actions v6 action metadata](https://github.com/gradle/actions/blob/v6/setup-gradle/action.yml)
- [Gradle Actions v6 distribution and caching choices](https://github.com/gradle/actions/blob/v6/DISTRIBUTION.md)
- [Checkout v7 metadata](https://github.com/actions/checkout/blob/v7/action.yml)
- [Setup Java v6 metadata](https://github.com/actions/setup-java/blob/v6/action.yml)
- [Upload Artifact v7 metadata](https://github.com/actions/upload-artifact/blob/v7/action.yml)
- [Failed broad upgrade run](https://github.com/qualified-opinion/lifetime-android/actions/runs/39091496112)

## Acceptance for this PR

Run the actual PR workflows on the new head:
- Build APK: unit tests, debug APK artifact upload, committed Room schema check, lint and minified release.
- Signed release PR smoke: unit tests and unsigned release build; signing, secret validation and publication remain gated to owner-started workflow_dispatch.
- Verify Gradle setup selects the basic cache provider and all setup steps complete.
- Record final CI evidence in the PR description. No signed release is published or phone result claimed.

## Next Android maintenance step

Handle runtime libraries separately from the toolchain:
1. Select one library family and keep the current AGP/Kotlin/wrapper versions. Check its exact release notes and minimum SDK/compile SDK requirements, then run tests, lint, debug and R8 builds. Media3/Coil upgrades need playback and thumbnail phone checks; Room needs schema and migration validation even without an intended entity change.
2. Put an AGP 9 / built-in Kotlin migration on its own branch, with compatible wrapper, Kotlin/Compose and KSP versions verified together from primary documentation. Audit Kotlin plugin application and compiler DSL before changing them.
3. Only combine further runtime updates after that toolchain branch is green and reviewed. Do not merge #14 as a shortcut.

This report selects an upgrade order and identifies the observed failure; it does not claim compatibility for untested runtime or toolchain versions. Phone acceptance from the batch 6 handoff is still pending.

## Progress after the Actions refresh
PR #19 merged as `e893414`. Build APK run 39547965764 and unsigned release run 39547965783 passed on `21f5c6b`; logs confirmed basic caching. The old grouped Actions proposal #11 is superseded by that implementation.

The first isolated runtime change, ExifInterface 1.3.7 to 1.4.2, merged in PR #20 (`22162e4`). Tests, debug APK, committed schema check, lint and R8 passed on `f1e7ba4` (run 39549903929). Its [official release notes](https://developer.android.com/jetpack/androidx/releases/exifinterface#1.4.2) describe the padded JPEG marker parser fix. See `../handoffs/batch6-exifinterface-update.md` for the real document-import regression, fixed-toolchain compatibility checks and phone retest. No other declared dependency or SDK level is raised by this change. The broad #14 toolchain migration remains unmerged.

## Room maintenance step
Select Room 2.7.1 to 2.7.2 for runtime, ktx and compiler together, using the existing shared version-catalog key. This is a patch within the current release series, not the grouped 2.8.5 / AGP 9 proposal from #14. The [official release notes](https://developer.android.com/jetpack/androidx/releases/room#2.7.2) document the patch and the 2.7 series' Kotlin 2.0-or-higher requirement; the project already uses Kotlin 2.1.21.

See `../handoffs/batch6-room-patch.md`. Keep the database at v2, retain the committed schema/identity hash and v1-to-v2 migration, and validate an old-schema v2 database before and after editing/inserting and reopening it. Runtime, compiler and KSP compatibility must pass the actual build without a toolchain or SDK increase. Room 2.8.x remains a separate compatibility decision; this does not claim 2.7.2 is the latest release.
