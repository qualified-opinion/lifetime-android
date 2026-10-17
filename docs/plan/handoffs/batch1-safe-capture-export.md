# Lifetime batch 1: safe capture and export (Kiln handoff pack)
Repo: qualified-opinion/lifetime-android, from main d75b7a2. Branch: fix/safe-capture-export
Engine: Codex builds; Claude Code reviews (Jan's instruction). Merge needs Jan's OK.

Goal: a capture or export can never silently lose data or leave junk behind.

Scope
1. Capture can't be lost: in CameraViewModel.onCaptured / MomentRepository.addCaptured, commit the file and insert the row first (no location). If location tagging is on, fetch location afterwards and update the row (new DAO updateLocation). Today the 5 s location wait precedes commit; if the process dies then, cleanUpIncoming() deletes the temp file on next start.
2. Capture errors handled: on failure delete the temp file, no crash, show "Couldn't save that capture". Currently no try/catch.
3. Export: in exportZip, on failure or cancel delete the partial target document and report failure; never report success on a partial zip. Don't swallow CancellationException in TimelineViewModel.export; rethrow after cleanup.
4. Tests: JVM unit tests for each fix (fakes ok). Existing tests stay green.

Out of scope: picker date/GPS fallback, ACCESS_MEDIA_LOCATION, timezone (batch 2); strip GPS on export (batch 2 optional); lint/R8/CI (batch 3); FLAG_SECURE/app lock (batch 4). No new deps, no UI redesign, no schema bump (DB stays v1; only one new DAO query).

Acceptance
- Killing the app during the location wait still leaves the capture in the timeline (test + PR note).
- Forced exception while filing a capture: no file left in incoming/, no crash, message shown.
- Failed/cancelled export leaves no partial zip at the target; UI shows an error, not "Exported".
- Existing tests + CI build pass; no new INTERNET or storage permission.
- Diff limited to the files above plus tests.

Risks
- Location-after-commit reorders operations: make sure the update can't clobber a later change.
- Deleting the partial target needs the document URI; if the provider can't delete it, say so in the PR and show a clear error.
- CameraX callback is on main thread: keep file work off it.

Process: post a short execution plan, wait for Jan's go, one PR, post link here. Kiln reviews (via Claude Code), one round.
