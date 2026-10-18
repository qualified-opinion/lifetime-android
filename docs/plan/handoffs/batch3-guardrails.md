# Batch 3: guardrails (lint, R8 and CI)

Repo: qualified-opinion/lifetime-android, from main `53fab94` (captions merged).
Branch: `claude/project-thread-rfyq2b` (the plan and the build are in the same PR).
Owner request: on 2026-10-18 Jan OK'd merging captions and said to move on to the next roadmap item, without a new build from main (the Signed release workflow is not run).
Builder: Claude Code. Independent review by a different engine and Jan's OK are still required before merge.

## Goal
Catch breakage in CI before it reaches the phone: lint errors, and problems that only show up in a minified release build. Nothing the app does changes for Jan, apart from one small import fix.

## Scope
1. **Lint in CI.** `build.yml` runs `lintDebug` after the unit tests and debug APK. Lint errors fail the build; warnings are listed in a text report printed in the log (`Show lint report` step). Real errors are fixed in code; a baseline is only used if an error is a false positive.
2. **R8 smoke test.** `build.yml` also runs `assembleRelease -Plifetime.minify=true`, which turns R8 on for that build only. The signed release keeps `isMinifyEnabled = false`, so what Jan installs is unchanged. Switching R8 on for real is a separate decision after a phone test of a minified build.
3. **Dependabot.** `.github/dependabot.yml`: monthly, grouped PRs for Gradle libraries and GitHub Actions. Dependabot pushes skip the "publish debug build" step (their token is read-only), so their CI stays green.
4. **Import leftover from the batch 2 review.** In `importItems`, a `CancellationException` thrown by one item while the import is still running now fails only that item (logged, file cleaned up, batch continues); a real cancel still stops the import. The other two leftovers were already done: a failed delete of an orphaned import is logged in `importOne`, and camera locations go through `MetadataParsing.validLocation` (the 0,0 check) in `updateLocation`.
5. **Docs.** Status table in `ROADMAP.md` (captions merged, batch 3 in this PR).

## Owner steps (GitHub settings, not in the PR)
- Branch protection on `main`: Settings > Branches > Add rule for `main`: require a pull request, and require the "build" status check to pass.
- Dependabot alerts and version updates: Settings > Code security. Version updates start once `dependabot.yml` is on `main`.

## Out of scope
- Turning R8 on for signed releases (needs a phone test of a minified build).
- Instrumented tests or an emulator in CI.
- Batch 4 (privacy hardening).

## Acceptance criteria
- CI runs `testDebugUnitTest assembleDebug`, the Room schema check, `lintDebug` and a minified `assembleRelease`, and is green on this PR.
- A lint error fails CI and its details are readable in the job log.
- `release.yml` and the signed APK it produces are unchanged (minify stays off without `-Plifetime.minify=true`).
- `ImportTest`: a stray `CancellationException` from one item's insert fails only that item, leaves no file without a row, and the next item imports; cancelling the import job still stops it with no leftovers.

## Risks
- **Lint noise.** Lint may flag existing code. Errors get fixed; warnings stay visible but do not fail CI.
- **Dependabot PRs.** Library bumps (Compose, Room, AGP) can need code changes. Each is a normal PR: review, CI green and Jan's OK before merge. Room bumps must keep the schema check green.
