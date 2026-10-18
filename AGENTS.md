# Lifetime: notes for coding agents

Start with `docs/plan/ROADMAP.md`: it says what is done, what is next and how work is run.

## Project
Private, offline-first Android timeline of photos and videos. Kotlin, Compose (Material 3), Room (version 2), CameraX, Media3, Coil. Package `app.lifetime`. See `README.md` for the layout.

## Rules
- No `INTERNET` permission, no analytics, no cloud. No storage permission either: media lives in app-private storage.
- Data safety first: never leave a file without a row or a row without a file. Commit the file, then insert the row; on failure clean up. Rethrow `CancellationException`.
- Room changes need a version bump, a non-destructive migration, a `MigrationTest` case and the updated `app/schemas` JSON committed.
- Pure logic (date parsing, metadata, fallbacks) goes in plain Kotlin with JVM tests; fake the Android parts.
- Keep diffs small and in scope of the batch pack. No new dependencies without a reason in the PR.

## Build and test
`./gradlew --no-daemon testDebugUnitTest assembleDebug` (this is what CI runs).

## Process
Work on a feature branch and open a PR. Never push to `main`, force-push, or create tags or releases. Update the status table in `docs/plan/ROADMAP.md` in every batch PR. Merge only after review and the owner's OK.
