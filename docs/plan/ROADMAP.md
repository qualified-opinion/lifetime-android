# Lifetime roadmap and handoff

This file is the single place to learn what is done, what is next, and how work on Lifetime is run. Any agent or person can take over from here, including the owner working alone with Claude Code. Keep it current: every batch PR updates the status table below.

**Last updated:** 2026-10-22. AGP 9 / built-in Kotlin migration merged in PR #27 (`4adb3b7`), completing the planned engineering work. Signed release v0.3.0 published from `4adb3b7` on 2026-10-22. Phone acceptance remains pending.

## What Lifetime is
A private, offline-first Android app that puts photos and videos in one calm timeline (year, month, day). You capture with CameraX or import original photos/videos with the system Files picker. Everything stays in app-private storage. There is no `INTERNET` permission, no accounts, no analytics and no cloud, and none of that changes. Package `app.lifetime`, `minSdk 26`, `targetSdk 35`, Kotlin, Compose (Material 3), Room (database version 2), Media3, Coil. Conventions are in `AGENTS.md`. Read it first.

## Where the work stands

| Batch | Branch | Scope | Status |
|---|---|---|---|
| MVP | n/a | Capture, import, timeline, viewer, delete, zip export, optional location | Merged (PR #1, 2026-10-16) |
| 1. Safe capture and export | `fix/safe-capture-export` | A capture or export can never silently lose data or leave junk | Merged (PR #3, `daac326`). Jan reports batch 1 and import/export working (2026-10-17); individual interruption scenarios not separately recorded. Pack: `handoffs/batch1-safe-capture-export.md` |
| 2. Import dates and location | `fix/import-date-gps` | Date fallback chain, `ACCESS_MEDIA_LOCATION`, reject 0,0, no orphan files, honest import message | Merged (PR #4, `c54cc12`). Jan reports dates correct and camera GPS working, but imported photo GPS missing; follow-up below. Pack: `handoffs/batch2-import-dates-gps.md`, review: `reviews/pr4-claude-review.md` |
| 2 follow-up. Import GPS and viewer controls | `fix/import-location-viewer-controls` | Original-file import; repeatable photo toolbar toggles | Merged (PR #6, `65ed66c`). Jan confirmed imported GPS and toolbar toggling work on his phone (2026-10-17). Pack: `handoffs/import-location-viewer-controls.md` |
| Signed release workflow | `claude/project-thread-49hd2l` | Owner-started workflow that builds, signs and publishes a release APK | Merged (PR #7, #8). Uses the owner's existing signing secrets; v0.2.0 published. v0.3.0 published 2026-10-22 from `4adb3b7` (run 39694331333; same signing cert as v0.2.0, version code 111) |
| Captions | `claude/project-thread-vv7hb1` | Write a caption right after capture or later in the viewer; shown under the photo in the timeline and kept in the export. First Room migration (v1 to v2) | Merged (PR #9, `53fab94`, 2026-10-18). Pack: `handoffs/captions.md` |
| 3. Guardrails: lint, R8 and CI | `claude/project-thread-rfyq2b` | Lint and a minified release build in CI, Dependabot, import cancellation leftover | Merged (PR #10, `7743a2f`, 2026-10-18). Branch protection on `main` turned on by Jan. Pack: `handoffs/batch3-guardrails.md` |
| 4. Privacy hardening | `claude/project-thread-0wngww` | Privacy screen (on by default), optional app lock, explicit backup and phone-to-phone transfer rules | Merged (PR #13, `90f846b`, 2026-10-18). Phone-to-phone transfer kept on (recommended default; Jan can still ask to block it). Pack: `handoffs/batch4-privacy-hardening.md` |
| 5. Restore from export | `claude/project-thread-0wngww` | Read a Lifetime export zip back into the timeline, adding and never replacing | Merged (PR #15, `d00e412`, 2026-10-21). Cancellation follow-up merged in PR #16. Pack: `handoffs/batch5-restore-from-export.md` |
| 5 follow-up. Restore cancellation | `fix/restore-scan-cancellation` | Stop ZIP scanning after cancellation in both passes | Merged (PR #16, `51ec760`, 2026-10-21). Tests, debug APK, schema check, lint and R8 passed on `d9a9173` (run 39542014075). |
| 6. Restore duplicate safety and release readiness | `docs/release-readiness-plan` | Content-aware restore deduplication in a separate code PR, phone acceptance and dependency triage | Plan merged in PR #17 (`eb7ccc0`). Duplicate fix merged in PR #18 (`057d137`), after independent review; tests, debug APK, schema, lint and R8 passed on `2ece771` (run 39545621054). Phone acceptance pending. Pack: `handoffs/batch6-release-readiness.md`. |
| 6 maintenance. CI Actions refresh | `chore/ci-actions-refresh` | Update workflow Actions using open-source basic Gradle caching; triage the broad Gradle upgrade separately | Merged in PR #19 (`e893414`). Build APK and unsigned release checks passed on `21f5c6b` (runs 39547965764 and 39547965783). Triage: `reviews/batch6-dependency-triage.md`. |
| 6 maintenance. Photo metadata library | `chore/exifinterface-1.4.2` | ExifInterface 1.4.2 only, with a padded JPEG marker import regression | Merged in PR #20 (`22162e4`). Tests (including padded JPEG import on APIs 28/33), debug APK, schema, lint and R8 passed on `f1e7ba4` (run 39549903929). Phone retest pending. Details: `handoffs/batch6-exifinterface-update.md`. |
| 6 maintenance. Room patch | `chore/room-2.7.2` | Room runtime/ktx/compiler 2.7.2; existing v2 persistence and v1 migration checks | Merged in PR #21 (`a55b4da`). Tests (including v2 reopen on APIs 26/33 and v1 migration), debug APK, schema, lint and R8 passed on `3a48737` (run 39551344760). Database remains v2. Pack: `handoffs/batch6-room-patch.md`. Phone retest pending. |
| 6 maintenance. Fragment patch | `chore/fragment-1.8.9` | Fragment 1.8.9; saving/restoring an INITIALIZED fragment in a FragmentActivity | Merged in PR #23 (`fda75a8`). Tests (including initialized-fragment save/restore on APIs 26/33), debug APK, schema, lint and R8 passed on `410e2b6` (run 39553665382). Phone authentication retest pending. Pack: `handoffs/batch6-fragment-patch.md`. |
| 6 maintenance. Video playback library | `chore/media3-1.8.0` | Media3 ExoPlayer/UI 1.8.0 with the existing SDK/toolchain | Merged in PR #24 (`0056cf3`) at the owner's request. Tests, debug APK, schema, lint and R8 passed on `a59f4ff` (run 39556054312). No independent review recorded; playback phone checks pending. Pack: `handoffs/batch6-media3-update.md`. |
| 6 maintenance. Image and video thumbnails | `chore/coil-3.2.0` | Coil compose/video 3.2.0 with the existing SDK/toolchain | Merged in PR #25 (`569537a`). Tests, debug APK, schema, lint and R8 passed on `23a3221` (run 39557278823). Photo orientation, zoom and video thumbnail phone checks pending. Pack: `handoffs/batch6-coil-update.md`. |
| 6 maintenance. Camera capture libraries | `chore/camerax-1.5.3` | CameraX core/camera2/lifecycle/video/view 1.5.3 with the existing SDK/toolchain | Merged in PR #26 (`9e65f05`). Tests, debug APK, schema, lint and R8 passed on `07dcd0c` (run 39558827196). Photo/video capture, camera switching and interruption phone checks pending. Pack: `handoffs/batch6-camerax-update.md`. |
| 6 maintenance. Build toolchain migration | `chore/agp9-built-in-kotlin` | AGP 9.4.1, Gradle 9.8.2, built-in Kotlin/Compose compiler 2.4.21 and KSP 2.3.12 | Merged in PR #27 (`4adb3b7`); CI green. Declared app libraries and SDK levels stayed fixed. #22 closed as superseded. Shipped in v0.3.0. Phone acceptance pending. Pack: `handoffs/batch6-toolchain-migration.md`. |

## Captions (merged, PR #9)
Jan wants Lifetime to replace social media, so a moment should carry its own words, like a post. Full plan in `handoffs/captions.md`. In short:
- After a photo or video, an optional "Add a caption" bar appears above the shutter. Skipping it changes nothing, so shooting in a row still works.
- The caption shows at the bottom of the viewer and can be added, edited or cleared there for any moment, imported ones included.
- In the timeline, a captioned moment shows like a post: the photo full width with its caption under it, in time order among that day's grid photos.
- Stored in a new nullable `caption` column (Room v2, manual migration, `MigrationTest`) and written to `moments.json` on export. The media file is never rewritten.
- Out of scope: writing captions into EXIF, hashtags or sharing, caption search, restore from export.

## Batch 3: guardrails (merged, PR #10)
Goal: catch breakage before it reaches the phone. Full plan in `handoffs/batch3-guardrails.md`. In short:
- CI runs `lintDebug`: errors fail the build, warnings are printed in the log.
- CI builds a minified release with `-Plifetime.minify=true`. Signed releases keep R8 off until a minified build is tested on the phone.
- `dependabot.yml`: monthly grouped PRs for Gradle and Actions. Branch protection on `main` is the owner's GitHub setting.
- Batch 2 review leftovers: a stray `CancellationException` now fails only that import item (`ensureActive()` in the catch). The orphan-delete log and the camera 0,0 check were already in place.
- CI still runs `testDebugUnitTest assembleDebug` and fails if the Room schema in `app/schemas` is not committed.

## Batch 4: privacy hardening (merged, PR #13)
Goal: someone holding the unlocked phone, or looking at recent apps, does not see the timeline. Full plan in `handoffs/batch4-privacy-hardening.md`. In short:
- **Block screenshots** (menu switch, on by default): `FLAG_SECURE`, so recents shows a blank card and screenshots and screen recordings are blocked.
- **App lock** (menu switch, off by default): the phone's own fingerprint, face or PIN via `BiometricPrompt`. Locks on a cold start and after 60 seconds in the background; unlocking returns to the same screen. If the phone loses its screen lock, the app opens and turns the lock off rather than lock Jan out.
- **Backup:** Google cloud backup stays off. Phone-to-phone transfer (cable or direct Wi-Fi, Android 12+) keeps carrying media, database and settings to a new phone, now written down in `data_extraction_rules.xml`; kept as the recommended default (it works without making an export first).
- New dependencies: `androidx.biometric` 1.1.0 and `androidx.fragment` 1.8.6 (`MainActivity` is now a `FragmentActivity`). No permission changes besides the library's normal `USE_BIOMETRIC`.

## Later ideas, not scheduled
Only when the owner asks.
- Store the capture timezone offset per moment (needs a Room v2 migration and a `MigrationTest`). Today the timeline uses the phone's current zone. Revisit only if the owner travels a lot and it bites.
- Original-file selection is now part of the batch 2 follow-up, authorized after Jan reported missing imported GPS. Further provider-specific work depends on retesting.

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
Jan reported on 2026-10-17: batch 1 works, import/export works, dates are correct, and camera location works. Imported gallery photos lose location; photo viewer controls cannot be restored after hiding. Phone model, Android version and installed build were not supplied.

Follow-up build (`86f31e3`) retested by Jan on 2026-10-17: imported photo GPS and repeated toolbar toggling both work. The retest list (only the first two are confirmed):
- Import a known geotagged original through Files (prefer local `DCIM/Camera`); confirm coordinates in Details and the exported photo. Gallery-only or already-stripped metadata cannot be reconstructed.
- Tap a photo repeatedly to hide/show the toolbar; check Share, Info and Delete remain reachable. Check double-tap/pinch zoom and swiping between photos and videos.
- Confirm date ordering, multi-select import and ordinary import/export still work.

Still not individually documented: process death during the post-capture location wait, and partial-ZIP cleanup after failed/cancelled export. Jan's broad batch-1 pass is recorded above without claiming each fault scenario was reproduced.

## Facts a new owner needs
- Media is stored in app-private storage (`files/media/YYYY/MM/...`), so uninstalling the app deletes it. **Export everything** (zip with `moments.json`, which includes captions) is the backup, and **Restore from export** reads it back in (adds only, skips moments only when time, type, size and SHA-256 media content match; works between the debug and signed apps).
- Order of operations on capture: commit the file and insert the row first, fetch location afterwards and update the row.
- Date for an imported item comes from, in order: embedded EXIF or video date, provider date taken, date parsed from the file name, provider last modified, then now. Items that fell back to now are counted in the import message.
- A location of exactly 0,0 is rejected everywhere it is parsed.
- Room entity changes need a version bump, a non-destructive migration and a `MigrationTest` case. Room is at version 2 since the captions batch (first migration: adds `caption`). Export a backup before installing a build that changes the database. The schema in `app/schemas` must be committed (CI checks it).
- Permissions today: camera, microphone, coarse and fine location (optional, off by default), `ACCESS_MEDIA_LOCATION` (requested at runtime on first import), and `USE_BIOMETRIC` from the biometric library (install-time, for the app lock). Do not add `INTERNET` or storage permissions.
- Restore: timeline menu > Restore from export (.zip) reads an export back in. It only adds moments, skips ones already there (same time, type, size and SHA-256 media content), and works across the debug and signed apps.
- Privacy: screenshots are blocked by default (timeline menu > Block screenshots). The app lock is off by default and uses the phone's own unlock; there is no separate app PIN to forget.
- Every push builds a debug APK and publishes it as a prerelease named after the branch (not for Dependabot). The owner installs from the Releases page. CI also runs lint and a minified release build.
- Signed releases: the owner runs **Signed release** from the Actions tab on `main` with a version such as `0.2.0`. It tests, builds, signs with the keystore in the `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets, and publishes release `v<version>`. Losing that keystore means the app can no longer be updated in place, so keep a backup. The signed app (`app.lifetime`) is separate from the debug app (`app.lifetime.debug`) and does not see its moments.
