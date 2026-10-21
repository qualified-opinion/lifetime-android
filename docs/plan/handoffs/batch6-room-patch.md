# Batch 6: Room 2.7.2 patch

Repo: qualified-opinion/lifetime-android, from main `22162e4` (PR #20 merged).
Branch: `chore/room-2.7.2`.
Owner request: Jan asked to continue on 2026-10-21.
Builder: Codex. Keep unmerged for independent review and Jan's approval.

## Goal and scope
Update Room runtime, ktx and compiler together from 2.7.1 to 2.7.2 through the existing shared `room` catalog key. Keep this within the current 2.7 series; the grouped Android/toolchain update in #14 remains separate.

The [official Room release notes](https://developer.android.com/jetpack/androidx/releases/room#2.7.2) document this patch and the 2.7 series' Kotlin 2.0-or-higher requirement. The project already uses Kotlin 2.1.21. This is maintenance, not a claim that a known Lifetime defect is reproduced by the vendor's fixes or that 2.7.2 is the latest Room release.

Keep AGP, Kotlin, KSP, Gradle wrapper, all other declared library versions and SDK levels unchanged. Keep `LifetimeDatabase` at version 2, the entity/DAO definitions, committed schema JSON and `MIGRATION_1_2` unchanged. A library version update alone does not introduce a new database schema.

## Data-preservation coverage
- Continue running the existing v1-to-v2 migration test.
- Add a regression on APIs 26 and 33 that creates a database using the frozen SQL, index and identity hash from the v2 schema exported by Room 2.7.1.
- Seed an existing captioned, geotagged moment with known IDs, dates, dimensions and size. Open using the new runtime and verify every field.
- Edit its caption and insert a second moment. Close and reopen, then verify both records and auto-generated ID behavior. The database must remain version 2.
- Keep real-Room restore and duplicate-safety tests in the suite. No destructive fallback or clearing user data is permitted.

## Automated acceptance
Run testDebugUnitTest, assembleDebug, the committed-schema check, lintDebug and minified assembleRelease. Schema JSON must remain unchanged; investigate any drift rather than introducing a version bump merely to satisfy a dependency update. The compiler and runtime must work with the existing Kotlin/KSP/AGP and SDK settings.

## Phone acceptance (pending)
Export a backup before installing. Update in place using the same app variant and signing identity; do not uninstall. Confirm existing timeline, captions, dates and GPS remain, edit a caption, add/import a moment, restart, and confirm the new and old records remain. Export/restore checks from the batch 6 plan remain pending until Jan reports them.

## Out of scope
Room 2.8.x, AGP 9, Kotlin or KSP migrations, entity/DAO/schema changes, new features, new permissions and signed release publication.
