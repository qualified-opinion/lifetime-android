# Lifetime batch 2: import dates and location are right (Kiln handoff pack)
Repo: qualified-opinion/lifetime-android, from main daac326. Branch: fix/import-date-gps
Engine: Codex builds; Claude Code reviews (Jan's instruction). Merge needs Jan's OK. Jan's go for batch 2 given in the room, 2026-10-16.

Goal: an imported photo or video lands on the day it was taken, with its real location when the file has one, and the app says so when it had to guess.

What is wrong today (read from main)
- The system photo picker redacts GPS from the files it hands over (Android 10+) unless ACCESS_MEDIA_LOCATION is held and the original is opened with MediaStore.setRequireOriginal. We never do, so imported geotags are mostly lost.
- Date fallback: embedded date, then provider columns, then "now". Picker URIs rarely expose datetaken, and date_modified is often the copy/restore date, so photos silently land on today or a wrong day with no sign.
- A GPS of exactly 0,0 ("null island") is accepted as a real location in photo EXIF and video ISO-6709.
- importOne: if the DB insert fails after commit, the committed file is orphaned (same class of bug as batch 1).

Scope
1. Location survives import: add ACCESS_MEDIA_LOCATION to the manifest. Ask for it at runtime (API 29+) on the first Import tap, with a one-line reason, once. If denied, import without location and do not ask again that session. When granted, open image/video sources via MediaStore.setRequireOriginal(uri) where the URI is a MediaStore URI; on SecurityException or UnsupportedOperationException fall back to the plain URI. Read EXIF/video location from the copy as today.
2. Date fallback chain, in order: embedded EXIF/video date; provider datetaken/date_taken; date parsed from the display name (IMG_20261016_143215, PXL_/VID_ variants, "2026-10-16 14.32.15", date-only like IMG-20261016-WA0001 means 12:00 local); provider last_modified; only then now. Drop date_modified from the list. Pure parsing goes in MetadataParsing with JVM tests.
3. Honest import message: after import say how many were filed under today because no date was found, e.g. "Added 12 moments (3 had no date, filed under today)". Keep the existing "couldn't be read" suffix.
4. Reject 0,0 as a location in MetadataParsing (EXIF and ISO-6709 paths).
5. No orphans: if insert fails after storage.commit in importOne, delete the committed file, then rethrow so the item counts as failed. Cancellation must not leave a committed file without a row.
6. Batch 1 nit: MomentRepository.updateLocation only writes EXIF when the guarded SQL update changed a row (use the DAO's affected-row count).
7. Tests: JVM unit tests for each of 2, 4, 5, 6 (fakes ok). Requirement 1 cannot be unit-tested end to end; test the fallback logic with a fake opener and say so in the PR.

Out of scope (cut on purpose)
- Per-moment timezone / storing the capture offset. Needs a DB v2 migration. Timeline stays in the phone's current zone. Revisit only if Jan travels a lot and it bites.
- Strip GPS on export. ExifInterface can't edit MP4 location, so a half-strip would give a false sense of privacy. Dropped from the plan, not deferred.
- Lint, R8, CI guardrails (batch 3). FLAG_SECURE, app lock (batch 4). No new deps, no INTERNET, no storage permission, no UI redesign, no schema bump (DB stays v1).

Acceptance
- Importing a geotagged photo through the picker on a phone keeps its location (Jan phone-tests; PR must say what was and wasn't verified without a device).
- An undated file named like PXL_20261016_143215.jpg lands on 16 Oct 2026, not today.
- A file with no date and no parsable name is filed under today and the import message counts it.
- 0,0 never becomes a location.
- Forced insert failure in importOne leaves no file behind and counts as failed.
- updateLocation on an already-located row does not touch EXIF.
- Existing 31 tests + CI build pass. Only new permission: ACCESS_MEDIA_LOCATION, requested at runtime. Diff limited to manifest, MomentRepository, MetadataParsing, TimelineViewModel, the import launcher in TimelineScreen, MomentDao if needed, plus tests.

Risks
- setRequireOriginal behaviour on photo-picker URIs varies by Android version. Treat the fallback as the safe path and report on a real device which path ran. If the picker never returns location even with the permission, say so in the PR and do not switch pickers in this batch; Kiln and Jan decide next.
- Filename date parsing can be wrong (renamed files). Only use it after embedded and provider dates, and keep patterns strict.
- Permission prompt must not block import: denied still imports.

Process: post a short execution plan, wait for Jan's go, one PR, post link here. Kiln reviews via Claude Code, one round.
