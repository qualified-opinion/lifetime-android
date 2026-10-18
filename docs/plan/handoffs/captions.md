# Batch: captions on moments

Repo: qualified-opinion/lifetime-android, from main `238c54e`.
Branch: `claude/project-thread-vv7hb1` (PR #9; the plan and the build are in the same PR).
Owner request: Jan asked on 2026-10-18 for a way to write a description right after taking a picture, "just like a social media post", because Lifetime is meant to replace social media, and for the text to be visible in the timeline. He said go the same day.
Builder: Claude Code. Independent review by a different engine and Jan's OK are still required before merge.

## Goal
Every photo or video can carry a short text, written right after capture or added later, shown with the moment in the viewer and kept in the export. Writing a caption never puts the capture itself at risk.

## What Jan will see
1. Take a photo or finish a video. A small "Add a caption" bar slides up above the shutter, showing the new capture's thumbnail. Tap it to open a text box; tap away or take another shot to skip. Nothing is forced: shooting several photos in a row works as today.
2. In the text box, type freely (several lines are fine) and tap **Save**. The caption is stored with that moment.
3. In the viewer, the caption sits at the bottom of the photo or video, above the toolbar, and hides and shows with the toolbar. Long captions show three lines ending in "…"; tap the text to expand it.
4. A pencil (or "Add caption" when empty) in the viewer edits the caption of any moment, including imported ones and older ones. Clearing the text removes the caption.
5. In the timeline, a moment with a caption shows like a post: the photo or video full width, with the caption written under it (up to four lines, then "…"; tap opens the viewer with the full text). Moments without a caption stay in the day's photo grid as today. Within a day, everything keeps its time order, so a post sits between the grid photos taken before and after it.
6. **Export everything** includes each caption in `moments.json`.

## Scope
1. **Room v2.** Add a nullable `caption TEXT` column to `moments`. Bump `LifetimeDatabase` to version 2 with a manual `MIGRATION_1_2` (`ALTER TABLE moments ADD COLUMN caption TEXT`), register it in `create()`, and commit `app/schemas/.../2.json`. No destructive fallback. This is the app's first migration.
2. **Migration test.** Add `MigrationTest` (Robolectric, so CI runs it in `testDebugUnitTest`) that builds a v1 database by hand from the v1 schema with a row in it, opens it with the real `LifetimeDatabase` and `MIGRATION_1_2`, and checks the row survives with `caption` null. Room validates the migrated table against the v2 entity on open, so a wrong migration fails the test. This needs no new dependency (`room-testing`'s `MigrationTestHelper` was the alternative).
3. **Data layer.** `MomentDao.updateCaption(id, caption)`; `MomentRepository.setCaption(id, text)` trims the text, stores null for blank, and caps length at 2,000 characters (enforced in a plain Kotlin `Captions.normalize()` with JVM tests). Saving a caption is a single row update: it never touches the media file, so the file/row safety rules are unaffected.
4. **Camera.** After `addCaptured` succeeds, show the "Add a caption" bar for `lastCapture`. The caption sheet saves through `setCaption` on the app scope so closing the camera right after Save still stores it. The draft text uses `rememberSaveable` so a rotation does not lose it. A failed save shows "Couldn't save the caption" and keeps the draft open. The capture is already in the timeline before the bar appears; skipping the caption changes nothing.
5. **Viewer.** Show the caption overlay (scrim for legibility, three-line collapse), and an edit action that opens the same caption sheet. Info sheet gets a "Caption" row only if that reads better on the phone; not required.
6. **Timeline.** Add a `TimelineRow.Post(moment)` row. In `buildTimeline`, a captioned moment becomes a `Post`; the uncaptioned moments between posts are laid out with the existing 1/2/4/3-column rules, so the day's order is unchanged. The `Post` row shows the media full width (video badge as on thumbnails) and the caption under it in body text, four lines max with ellipsis. Text is not drawn over the photo, so it stays readable on any image and any tile size. `dayIndex` and the jump-to-date scroll keep working.
7. **Export.** Add `"caption"` to each entry in `moments.json` when present. The index stays `"version": 1` because the field is optional and additive; older readers ignore it.
8. **Docs.** Update the status table in `ROADMAP.md` and the "Facts a new owner needs" section (Room is now version 2; export carries captions).

## Out of scope
- Writing captions into the photo or video file (EXIF `ImageDescription`, MP4 metadata). Editing would mean rewriting the original file each time, which the data-safety rules avoid. The database and the export are the record.
- Hashtags, mentions, links, formatting, emoji pickers beyond the keyboard, likes, comments or any sharing of captions to other apps. Lifetime stays private and offline.
- A separate note for a whole day (a day title or day caption). Possible follow-up once moment captions are in use.
- Searching captions, albums, captions on several moments at once. Possible later ideas.
- Restore from export. When it is built later, it must read `caption`.
- Batches 3 and 4.

## Acceptance criteria
- A v1 database with existing moments upgrades to v2 with every row intact and `caption` null (`MigrationTest`).
- `Captions.normalize()`: trims, blank becomes null, keeps inner newlines, caps at 2,000 characters (JVM tests).
- Repository: setting, editing and clearing a caption updates only that row; the media file's bytes are unchanged (JVM test with the existing fakes).
- Export: tested with real Room and JSON under Robolectric (`CaptionStorageTest`).
- `buildTimeline` (JVM tests): a captioned moment becomes a `Post` row in time order; uncaptioned moments around it keep the 1/2/4/3 layout rules; a day with no captions builds exactly as today; `dayIndex` still points at each day header.
- Export: a moment with a caption has `"caption"` in `moments.json`; one without has no key.
- `./gradlew --no-daemon testDebugUnitTest assembleDebug` passes in CI.
- On Jan's phone, on top of an existing install with moments (debug build): moments are still there after the update; take a photo, add a caption, see it in the viewer; see the caption under the photo in the timeline, in the right place among that day's photos; take three photos in a row without captioning; edit and clear a caption on an imported photo; export and find the caption in `moments.json`.

## Risks
- **First migration.** A wrong migration can crash on launch or, with a destructive fallback, wipe the timeline. Mitigation: manual migration, no `fallbackToDestructiveMigration`, migration test in CI, and Jan exports a backup before installing the build.
- **Signed app.** The signed `app.lifetime` install upgrades the same way when the next release is cut; Jan should export before installing it too.
- **Keyboard over the camera.** The caption sheet must handle IME insets so Save stays visible; check on the phone.

## References
- [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions) and `MigrationTestHelper`.
- Existing conventions: `AGENTS.md`, data-safety order in `ROADMAP.md`.
