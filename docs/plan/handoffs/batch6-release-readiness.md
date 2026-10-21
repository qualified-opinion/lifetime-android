# Batch 6: release readiness

Repo: qualified-opinion/lifetime-android.
Plan branch: `docs/release-readiness-plan`, updated against main `d00e412`.
Owner request: Jan asked to merge the current fix and proceed to the next plan on 2026-10-21.
Status: plan only. Restore is merged in PR #15 (`d00e412`, 2026-10-21). PR #16 now targets main for the missing cancellation fix; no release or device success is claimed.

## Goal
Confirm the completed features work together on Jan's phone before another signed release, and split dependency maintenance into reviewable changes.

## Prerequisites
- Obtain the required independent review for PR #16's updated head. Its application fix and tests are unchanged from `2728310`, which passed unit tests, debug build, schema check, lint and R8 in run 39514164693; CI must also pass on the refreshed head.
- Merge #16 directly into main. PR #15 is already merged, so its completed feature branch is no longer a target. Automatic approval review previously blocked #16 because independent review was missing; do not bypass that block.
- Preserve a known-good export outside app-private storage before device testing. Keep the installed app and its moments; no uninstall is needed to test restore.

## Phone acceptance checklist (Jan)
Record phone model, Android version, installed version/build and the result for each check.
- Export a small set including a captioned photo, a geotagged photo and a playable video. Restore into the other app variant (debug/signed) without uninstalling the source. Compare media counts, dates, captions, coordinates and video playback.
- Restore the same ZIP again. Confirm the count stays unchanged and the message reports existing moments.
- Keep an unrelated moment in the destination before restore. Confirm it and its caption remain unchanged.
- Turn App lock on; cancel enrollment once (it must stay off), then enable it successfully. Check cold start, return within 60 seconds, and return after 60 seconds; the same viewer page should remain after unlock.
- With App lock enabled, use Files import, Share, Export and Restore, including returning from a picker after more than 60 seconds. Authentication must work without losing the selected operation.
- Check screenshot blocking and the recent-apps card with the switch on and off. Confirm the preference survives restart.
- Check adding, editing and clearing captions; repeated toolbar taps; photo zoom; photo/video swiping.
- Record the remaining earlier fault checks separately: process death while capture waits for location, and cleanup after a cancelled/failed export. Broad earlier passes do not establish these scenarios.

## Automated work and dependency triage
- Run the existing CI suite on the final combined restore head: testDebugUnitTest, assembleDebug, schema check, lintDebug and minified assembleRelease.
- Inspect pending dependency PRs #11 and #14 separately. Record CI failures and compatibility constraints before deciding upgrade order.
- Keep Actions changes separate from Android/Gradle/Kotlin changes. Split broad runtime/toolchain updates into small, compatible groups; do not merge dependency PRs as part of this plan.
- File or fix reproducible acceptance failures on a feature branch with targeted regression tests. Data loss, crashes and lock-out take priority.
- Implement the content-aware duplicate fix below as a separate application PR before the next signed release.

## Required restore duplicate fix (separate PR)
The current time/type/size heuristic can silently skip a different file. Existing moments stay intact, but a genuine backed-up moment may never be added. Treat this as a release blocker, not merely a documented limitation.

- Keep time/type/size only as a candidate filter. Skip a candidate only when its media content also matches, using a streaming SHA-256 digest of the incoming file and the existing candidate files. Index new successful restores too, so duplicates within one ZIP behave consistently.
- If contents differ, restore both moments even when time, type and size match. Do not rewrite existing rows, media, captions or locations.
- Stream hashes on IO with cancellation checks and bounded memory. Reuse digests within one restore; no dependency or Room/schema change is expected.
- If an existing candidate file is missing or unreadable, do not silently treat it as a duplicate. Define and test failure behavior so incoming backed-up media is not skipped on an unverified match.
- Regression tests: different bytes with identical time/type/size both restore; exact repeats restore once; multiple collision candidates find a true byte match regardless of order; re-restoring a collision-containing ZIP adds nothing; cancellation during hashing leaves no temporary/orphan files and does not change existing moments.
- Keep old version-1 export ZIPs supported. Pass the full existing CI suite and preserve caption, date and GPS round-trip tests.

## Acceptance
- Each phone check has a recorded pass/fail/pending result tied to a build. Pending device checks remain explicit.
- The final combined restore commit has green CI and required review.
- The content-aware duplicate fix is merged after review and green CI; collision and repeated-restore regressions pass.
- Dependency triage produces a specific next maintenance change, with compatibility evidence and validation steps.
- Any discovered blocker has a linked fix or remains marked open before release.
- Update ROADMAP.md with verified status. A signed release remains a separate owner action; this plan does not publish one.

## Out of scope
New app features, cloud/network access, schema changes, broad dependency upgrades, enabling R8 for signed releases before phone validation, and automatic release publication.

## Risks
Debug and signed variants have separate databases. Test restore using both variants without deleting the only copy of any media. OEM authentication and recent-apps behavior require phone checks; JVM tests alone cannot establish them.
