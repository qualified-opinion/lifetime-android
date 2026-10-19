# Batch 5: restore from export

Repo: qualified-opinion/lifetime-android, from main `90f846b` (batch 4 merged).
Branch: `claude/project-thread-0wngww` (the plan and the build are in the same PR).
Owner request: on 2026-10-19 Jan said to continue after batch 4. Restore was proposed as batch 5 and built on the recommended default while Jan's go on the plan is pending.
Builder: Claude Code. Independent review by a different engine and the owner's OK are still required before merge.

## Why this is next
"Export everything" is the only backup, but nothing can read it back. That bites when switching phones without phone-to-phone transfer, after a reinstall, and when moving from the debug app (`app.lifetime.debug`) to the signed app (`app.lifetime`), which do not share moments. Every other roadmap item is done.

## Scope
1. **Restore from a Lifetime export.** Timeline menu > "Restore from export (.zip)" opens the Files picker for one zip. The app reads `moments.json` (version 1) and, for each entry, copies `media/<path>` into app-private storage and inserts a row with the exported date, type, mime type, size, location (0,0 still rejected) and caption. `source` is kept (camera stays camera).
2. **Adds, never replaces.** Existing moments are untouched. A moment already in the timeline (same capture time, type and file size) is skipped, so restoring the same zip twice adds nothing.
3. **Data safety, as for import.** Each file is written to `incoming/` first, then filed and its row inserted; on any failure that item's file is removed and the restore continues. A real cancel stops the restore with no leftovers. Entries whose file is missing in the zip, entries not under `media/`, unreadable dates or types, and unknown `version` values are refused, not guessed. Restored files are always filed under a fresh name by the app (year/month folder from the moment's date), so a name inside the zip can never write outside the media folder.
4. **Honest message.** "Restored 120 moments (3 already here, 1 couldn't be read)". Progress shows in the same bar as import and export.
5. **Docs.** Status table in `ROADMAP.md`; "Facts" says the zip can be restored.

## Out of scope
- Restoring zips made by other apps, or partial/merged exports.
- Replacing the timeline with the zip's contents, or deleting anything.
- Encrypted or password-protected exports.
- Restoring settings (privacy screen, app lock, location tagging).

## Acceptance criteria
- CI green (unit tests, debug APK, schema check with no Room change, lint, minified release).
- JVM tests (Robolectric, real Room): export then restore into an empty database gives the same moments, captions and locations; restoring twice adds nothing; a zip with a missing file, a `../` path or `version` 2 restores the good entries and refuses the bad ones with no orphan files; cancelling mid-restore leaves no file without a row.
- On the phone: export from the debug app, restore into the signed app, and the timeline matches (dates, captions, locations, videos play).

## Risks
- **Large zips.** `moments.json` is the zip's last entry, so the restore reads the zip twice in order: once for the index, once to stream each media file into place. Nothing is unpacked twice or loaded whole; the index is capped at 64 MB.
- **Duplicates detection** by time, type and size can miss a re-encoded copy; that only means a duplicate, never a loss.
