# Lifetime roadmap and handoff

This file is the single place to learn what is done, what is next, and how work on Lifetime is run. Any agent or person can take over from here, including the owner working alone with Claude Code. Keep it current: every batch PR updates the status table below.

**Last updated:** 2026-10-17, after batch 2 merged (`main` at `c54cc12`). Batches 3 and 4 are not started.

## What Lifetime is
A private, offline-first Android app that puts photos and videos in one calm timeline (year, month, day). You capture with CameraX or import with the system photo picker. Everything stays in app-private storage. There is no `INTERNET` permission, no accounts, no analytics and no cloud, and none of that changes. Package `app.lifetime`, `minSdk 26`, `targetSdk 35`, Kotlin, Compose (Material 3), Room (database version 1), Media3, Coil. Conventions are in `AGENTS.md`. Read it first.

## Where the work stands

| Batch | Branch | Scope | Status |
|---|---|---|---|
| MVP | n/a | Capture, import, timeline, viewer, delete, zip export, optional location | Merged (PR #1, 2026-10-16) |
| 1. Safe capture and export | `fix/safe-capture-export` | A capture or export can never silently lose data or leave junk | Merged (PR #3, `daac326`). Pack: `handoffs/batch1-safe-capture-export.md` |
| 2. Import dates and location | `fix/import-date-gps` | Date fallback chain, `ACCESS_MEDIA_LOCATION`, reject 0,0, no orphan files, honest import message | Merged (PR #4, `c54cc12`). Geotag retention not yet checked on a phone. Pack: `handoffs/batch2-import-dates-gps.md`, review: `reviews/pr4-claude-review.md` |
| 3. Guardrails: lint, R8 and CI | not started | see below | Needs a handoff pack and the owner's go |
| 4. Privacy hardening | not started | see below | Needs a handoff pack and the owner's go |

## Batch 3: guardrails (next)
Goal: catch breakage before it reaches the phone. Outline only; a full pack is written when the owner says go.
- Add Android lint to CI (`lintDebug`) and fix or baseline what it finds.
- A release (R8) build smoke test in CI, so minification problems show up before a release build is tried.
- Branch protection on `main` and Dependabot for Gradle and Actions (owner does the GitHub settings; a PR can add `dependabot.yml`).
- Small leftovers from the batch 2 review: log (do not ignore) a failed delete of an orphaned file in `importOne`; route a camera-capture location through the same 0,0 check as imports; call `ensureActive()` in the import catch so a stray `CancellationException` is not mistaken for a cancelled import.
- CI already runs `testDebugUnitTest assembleDebug` and fails if the Room schema in `app/schemas` is not committed.

## Batch 4: privacy hardening
Outline only; write the pack when the owner says go. Cut anything the owner does not want.
- `FLAG_SECURE` so the timeline does not show in the recents screen or screenshots.
- Optional app lock (device credential via `BiometricPrompt`), off by default.
- Decide then whether timeline photos should be excluded from device backup (`allowBackup` is already `false`).

## Later ideas, not scheduled
Only when the owner asks.
- Store the capture timezone offset per moment (needs a Room v2 migration and a `MigrationTest`). Today the timeline uses the phone's current zone. Revisit only if the owner travels a lot and it bites.
- A different picker if the system picker never returns location even with the permission (decide with the owner after the phone test).

## Decided against
- **Strip GPS on export.** `ExifInterface` cannot edit MP4 location, so a half-strip would give a false sense of privacy. Dropped on purpose, not deferred.
- Cloud sync, accounts, social features, analytics and any network access. They go against the offline and private brief.

## How work is run
1. **Plan:** Kiln (product strategist) writes a handoff pack for each batch: goal, repo and branch, scope and out-of-scope, acceptance criteria, risks and engine. Packs live in `handoffs/`.
2. **Go:** the owner (Jan) says go.
3. **Build:** Codex builds on the named feature branch, runs `./gradlew testDebugUnitTest assembleDebug`, and opens a PR. Every fix needs a test where a JVM test can show it.
4. **Review:** a different engine than the builder (Claude Code or Grok Build for Codex-written code) checks the diff against the pack's acceptance criteria and says approve or changes needed. After a fix, only the new commit is re-checked.
5. **Merge:** squash-merge, pinned to the exact commit that was approved, only when it is approved, CI is green and the owner has OK'd that PR.
6. **Never:** push to `main`, force-push, create tags or releases, or add network access. The owner publishes releases himself.
7. **Docs:** every batch PR updates the status table here, and `AGENTS.md` if a convention changed.

The coordination room is the "Coding" group chat in Grok Bot (Jan, Atlas, Kiln, Nightshift).

### When the owner works alone with Claude Code
Open the repo in Claude Code (it reads `CLAUDE.md`, which points to `AGENTS.md`). A good first message: "Read `docs/plan/ROADMAP.md` and `AGENTS.md`, then do the next batch. Make a feature branch, keep to the pack's scope, run the tests, update the status table, and open a PR." For a batch with no pack yet, ask Claude to write one in `docs/plan/handoffs/` in the same format as the existing ones and show it before building. For review, ask a fresh Claude session (or another tool) to review the PR diff against the pack's acceptance criteria.

## Open verification
Nothing below has been checked on a real phone:
- **Geotag retention on import:** pick a geotagged photo with the system picker and check the details sheet shows a location. It depends on the Android version and whether `setRequireOriginal` works for picker links. If it does not work, say which Android version and decide on the next step (see Later ideas).
- Killing the app during the location wait after a capture still leaves the capture in the timeline (batch 1).
- A failed or cancelled export leaves no partial zip (batch 1).

## Facts a new owner needs
- Media is stored in app-private storage (`files/media/YYYY/MM/...`), so uninstalling the app deletes it. **Export everything** (zip with `moments.json`) is the backup.
- Order of operations on capture: commit the file and insert the row first, fetch location afterwards and update the row.
- Date for an imported item comes from, in order: embedded EXIF or video date, provider date taken, date parsed from the file name, provider last modified, then now. Items that fell back to now are counted in the import message.
- A location of exactly 0,0 is rejected everywhere it is parsed.
- Room entity changes need a version bump, a non-destructive migration and a `MigrationTest` case. The schema in `app/schemas` must be committed (CI checks it).
- Permissions today: camera, microphone, coarse and fine location (optional, off by default), `ACCESS_MEDIA_LOCATION` (requested at runtime on first import). Do not add `INTERNET` or storage permissions.
- Every push builds a debug APK and publishes it as a prerelease named after the branch. The owner installs from the Releases page.
