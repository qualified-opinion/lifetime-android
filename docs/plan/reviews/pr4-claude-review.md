# PR #4 review (batch 2), by Claude Code via Kiln

First-round verdict on the diff only. The branch was fixed and Kiln approved the final head `d261377` (merged as `c54cc12`). Items 2 and 3 below (location on Android 10 and 11 file-browser links, and the need for a phone test) are the reason the geotag check on a real phone is still open.

**Verdict: changes needed.** Only the source diffs were available, not the repo, so I couldn't run the tests or try anything on a device. The date chain, the 0,0 rejection, the no-orphan cleanup, the location-update check and cancellation are all correct and well tested. The fixes needed are small.

**Must fix**
1. **Common real filenames aren't recognised as dates (criterion 2).** All three filename patterns must match the whole name and allow only one `.ext`. So these fall through to `last_modified` or "now":
   - Pixel names with an extra suffix: `PXL_20261016_143215123.MP.jpg`, `.PORTRAIT.jpg`, `.NIGHT.jpg`.
   - WhatsApp videos named `VID-20261016-WA0001.mp4`. Only `IMG-…-WA` is matched, but the brief's "like IMG-20261016-WA0001" covers videos too.

   Fix: allow repeated extensions (`(?:\.[A-Za-z0-9]+)*`) and change the WhatsApp pattern to `(?:IMG|VID)-(\d{8})-WA\d{4}`. Add test cases for both.

**Should fix / acceptance gaps**

2. **Location is still lost on some devices (criterion 1).** The original file is only requested when the link comes from the main media store (`uri.authority == MediaStore.AUTHORITY`). On Android 10 and 11 devices without the system photo picker, the picker falls back to the file browser. That returns `com.android.providers.media.documents` links, so the user is asked for the permission and location is still stripped. Either convert the link with `MediaStore.getMediaUri()` before `setRequireOriginal`, or state the limitation in the PR.
3. **Criterion 1 needs a phone test.** Whether picker links keep GPS with `setRequireOriginal` depends on the Android version. The PR must say which path ran on a real phone, as the handoff requires. Also check whether Android 10–12 shows any dialog for ACCESS_MEDIA_LOCATION. If not, the "Allow photo location access…" Toast points at nothing.
4. **Date-only filename matching is loose.** A bare 8-digit name like `20261016.jpg` is accepted with no prefix, which goes against "keep patterns strict". There is also no upper limit, so `IMG_20991231.jpg` gets filed in 2099. Suggest requiring a prefix and rejecting dates more than a day ahead.
5. **The provider-metadata read has no test.** Nothing checks which provider columns are used (display name, `last_modified`) or that `date_modified` is no longer read. The removal only shows in `DATE_COLUMNS`.

**Risky behaviour I checked (acceptable)**
- **The permission prompt never blocks import.** The picker opens after any answer, including an instant auto-deny. The "asked once" flag lives in the ViewModel, so it resets when Android kills the app process, which is fine for once per session.
- **Cancellation:**
  - The copy stops between chunks.
  - Commit and insert run together in a block that can't be cancelled. If the insert fails, the committed file is deleted, the error is rethrown and the item counts as failed.
  - If cancellation arrives mid-insert, the file and its row are both kept, and the batch stops before the next item.
  - The tests cover all of this.
- **Stray cancellation errors.** `importItems` rethrows any CancellationException, even one not caused by the import being cancelled (for example from inside Room). The batch would then end with no message. Minor; calling `ensureActive()` in the catch would be stricter.
- **Import message (criterion 3).** The "no date" count only goes up when an item succeeds, and the wording matches the spec.
- **`updateLocation` (criterion 6).** It returns early when the update changed no row, so EXIF is not touched. The DB stays at v1.

**Nits**
- If deleting the committed file itself fails, the result is ignored and the file stays behind silently. Log it.
- A slow read from the source can't be interrupted. This was already the case before this PR.
- The Toast text is hard-coded English, which matches the rest of the app.
- A 0,0 device location on the camera-capture path isn't rejected. That's out of scope, but routing it through `validLocation` is cheap.
- Scope is clean: only ACCESS_MEDIA_LOCATION was added, no INTERNET, no new dependencies, and the diff stays within the allowed files.

