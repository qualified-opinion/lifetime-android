# Batch 4: privacy hardening

Repo: qualified-opinion/lifetime-android, from main `7743a2f` (batch 3 merged).
Branch: `claude/project-thread-0wngww` (the plan and the build are in the same PR).
Owner request: on 2026-10-18 Jan turned on branch protection for `main` and said to proceed to batch 4. No new build from main (the Signed release workflow is not run).
Builder: Claude Code. Independent review by a different engine and Jan's OK are still required before merge.

## Goal
Someone who picks up the unlocked phone, or looks at the recent-apps screen, should not see the timeline. Nothing about capture, import, export or captions changes.

## Scope
1. **Privacy screen (on by default).** `FLAG_SECURE` on the one activity, so the timeline is blank in the recent-apps screen, in screenshots, screen recordings and screen sharing. A switch in the timeline menu ("Block screenshots") turns it off, for example to take a screenshot for a bug report. Stored in `AppPreferences`, applied before the first frame.
2. **App lock (off by default).** A switch in the timeline menu ("App lock"). Turning it on asks for the phone's own unlock (fingerprint, face or PIN/pattern/password through `BiometricPrompt` with `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`), so Jan proves it works before it is on. While on:
   - Lifetime is locked on every cold start and after it has been in the background for 60 seconds or more. The short grace period covers the Files picker, Share, the export "Save as" dialog and the unlock screen itself.
   - The lock screen covers everything (the timeline underneath is hidden from touch and from TalkBack) and keeps the navigation state, so unlocking returns to the same moment. Back from the lock screen leaves the app.
   - The unlock prompt opens on its own once; after a cancel, an "Unlock" button asks again.
   - The recent-apps thumbnail is hidden on Android 13+ even when the privacy screen is off.
   - **No lock-out.** If the phone no longer has a screen lock, nothing can be checked, so the app opens and turns the lock off with a message, instead of locking Jan out of his moments. Turning the lock off needs no unlock (whoever sees the switch is already inside).
   - Lock state lives in `AppContainer` (survives rotation and theme changes; a killed process starts locked). The timing rules are plain Kotlin (`AppLock`) with JVM tests.
3. **Backup and transfer.** `allowBackup="false"` already keeps Lifetime out of Google cloud backup. On Android 12+ that flag does not cover the setup wizard's phone-to-phone transfer (cable or direct Wi-Fi, no cloud). Decision (asked Jan, recommended default while waiting): keep the transfer, so switching phones keeps the timeline, because restore-from-export does not exist yet. `data_extraction_rules.xml` makes this explicit: cloud backup excludes everything; device transfer includes only the media folder, the database and the settings (not the `incoming` temp folder). If Jan picks "block", the device-transfer section excludes everything instead.
4. **Docs.** Status table in `ROADMAP.md` (batch 3 merged, batch 4 in this PR); permissions/facts section mentions the privacy screen and app lock.

## New dependency
- `androidx.biometric:biometric:1.1.0` (latest stable): the supported way to ask for fingerprint/face or the device PIN with one prompt on Android 8 to 15. Doing it by hand needs three APIs (`KeyguardManager` on 8 to 9, framework `BiometricPrompt` on 9+, `setAllowedAuthenticators` on 11+).
- `androidx.fragment:fragment:1.8.6`: `BiometricPrompt` needs a `FragmentActivity`. Biometric 1.1.0 pulls in fragment 1.2.5, which predates the activity-result API the rest of the app uses; pinning a current fragment avoids mixing the two. `MainActivity` becomes a `FragmentActivity` (a subclass of `ComponentActivity`, so Compose and the launchers behave the same).
- No permission changes: `USE_BIOMETRIC` comes from the library manifest and is a normal (install-time) permission. Still no `INTERNET` or storage permission.

## Out of scope
- Encrypting the media files or the database at rest (app-private storage is already sandboxed; encryption needs key management and a migration of every file).
- A separate app PIN, hidden or locked albums, a decoy mode.
- Locking the zip export with a password.
- Changing what Share sends (sharing a moment is an explicit choice).

## Acceptance criteria
- CI is green: unit tests, debug APK, Room schema check (no schema change), lint and the minified release build.
- Fresh install: recent-apps shows a blank Lifetime card and screenshots are blocked; after turning "Block screenshots" off, both work again, and the choice survives a restart.
- App lock off (default): nothing changes when leaving and returning.
- Turning app lock on asks for the phone unlock first; cancelling leaves it off.
- App lock on: a cold start asks for the unlock; returning within 60 seconds does not; returning after 60 seconds does; the viewer page you were on is still there after unlocking. Import through Files, Share and Export work without being locked out mid-way.
- Removing the phone's screen lock while app lock is on: the app opens and says the lock was turned off.
- `AppLockTest` covers cold start, grace period, relock, rotation-safe state, turning the lock on/off and the no-screen-lock fallback. `AppPreferencesTest` covers the defaults (privacy screen on, lock off) and persistence.

## Risks
- **Screenshots blocked by default.** Jan may want screenshots for bug reports; the switch is one tap in the menu.
- **Lock-out.** Covered by the no-screen-lock fallback; the phone's own PIN always works as the fallback for fingerprint/face.
- **Old fragment/activity interplay on Android 8 to 9** (device-credential prompt goes through a separate system screen). Covered by pinning fragment 1.8.6; needs a check on an old phone if Jan has one.
- **Phone-to-phone transfer** carries the timeline to the new phone, which is the point; it never goes through the cloud.
