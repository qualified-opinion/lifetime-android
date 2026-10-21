# Batch 6: final build toolchain migration

Repo: qualified-opinion/lifetime-android.
Branch: `chore/agp9-built-in-kotlin`, based on main `9e65f05` after PR #26.
Owner explicitly authorized this final PR on 2026-10-21.

## Goal and scope
Migrate the build to AGP 9's built-in Kotlin separately from Dependabot's broad #22 proposal. Select AGP 9.4.1, Gradle 9.8.2, Kotlin/Compose compiler 2.4.21 and KSP 2.3.12. Keep Java 17, compile/target SDK 35, minSdk 26 and all declared app/test library versions fixed. The Kotlin standard library follows the upgraded compiler; no runtime feature or permission is added.

Remove the Kotlin Android plugin from both build files and the catalog. Explicitly align AGP's KGP build classpath with the Compose compiler using the shared Kotlin version. Remove android.kotlinOptions: built-in Kotlin inherits JVM 17 from android.compileOptions. Configure test logging through Gradle Test tasks. Keep Android resource support and default-return stubs for Robolectric tests. Keep Room schema generation and signing configuration intact.

Update the wrapper scripts/JAR from Gradle's v9.8.2 source tag (which bootstraps with the same published 9.8.1/9.8.2 wrapper JAR). Verify the wrapper JAR SHA-256 is `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`. Pin the 9.8.2 binary distribution checksum to `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`.

## Primary compatibility evidence
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes): Gradle minimum 9.6.0, JDK 17 and Build Tools 36.0.0. Build Tools can advance without changing app compile/target SDK.
- [AGP 9.0 migration and higher KGP selection](https://developer.android.com/build/releases/agp-9-0-0-release-notes): built-in Kotlin/new DSL and explicit KGP buildscript classpath.
- [Built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin): remove Kotlin Android plugin and old kotlinOptions; JVM target inherits Java target.
- [Kotlin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html): Kotlin 2.4.21's fully tested range stops at Gradle 9.7.0 / AGP 9.3.1. It permits newer versions with possible warnings; do not describe this newer pairing as covered by that table. Actual CI is required.
- [KSP 2.3.12 release](https://github.com/google/ksp/releases/tag/2.3.12) and [KSP2 design](https://github.com/google/ksp/blob/2.3.12/docs/ksp2.md): KSP2 is separate from the Kotlin compiler plugin implementation. Verify Room processing with the actual compiler/schema/tests.
- [Gradle checksums](https://gradle.org/release-checksums/) and [wrapper source](https://github.com/gradle/gradle/tree/v9.8.2/gradle/wrapper).

## Acceptance
Both PR workflows must pass on the exact final head:
- Build APK: existing unit tests, debug APK/artifact, committed Room schema check, lint and minified release.
- Signed release PR smoke: tests and unsigned release assembly. Secret validation/signing/publication stay gated to owner-started workflow_dispatch.
- Verify no Room schema changes, SDK increases, Kotlin Android plugin, legacy-DSL opt-out or built-in-Kotlin opt-out. No new app behavior is requested; build integration and existing regressions provide validation.

Record CI in the PR description. A green build does not establish phone acceptance. Camera, playback, restore, app lock and screenshot checks remain pending under the batch 6 release-readiness checklist.

## What follows
After independent review and owner approval, merge this PR. Then recheck #22, record its unselected library updates as deferred maintenance, and close it as superseded rather than merging the bundle. No further dependency updates are required merely to finish this batch. Run phone acceptance, fix any demonstrated blockers, then the owner can start a signed release. Do not close #22 or publish a signed release as part of preparing this PR.
