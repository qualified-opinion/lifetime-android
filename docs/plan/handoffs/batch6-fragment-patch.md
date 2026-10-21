# Batch 6: Fragment 1.8.9 patch

Repo: qualified-opinion/lifetime-android, from main `a55b4da` (PR #21 merged).
Branch: `chore/fragment-1.8.9`.
Owner request: Jan asked to continue on 2026-10-21.
Builder: Codex. Keep unmerged for independent review and Jan's approval.

## Goal and scope
Update the existing Fragment library from 1.8.6 to 1.8.9. MainActivity extends FragmentActivity for BiometricPrompt. The [official release notes](https://developer.android.com/jetpack/androidx/releases/fragment#1.8.9) document fragment lifecycle, state-saving and back-stack fixes within this release series. This is an isolated maintenance update, not a claim that Lifetime reproduces every listed vendor bug.

Keep Biometric 1.1.0, other declared libraries, SDK levels, AGP, Kotlin, KSP and wrapper unchanged. Do not alter app-lock timing, privacy flags, preferences, activity code, authentication choices, permissions, data or schema.

## Regression coverage
Use real FragmentActivity and FragmentManager under Robolectric on APIs 26 and 33:
- Add a fragment capped at Lifecycle.State.INITIALIZED before onCreate; give it an argument.
- Save the host activity and destroy it. No creation or user-state-saving callback should run for that not-yet-created fragment.
- Restore a fresh host from the saved Bundle and verify the tagged fragment, argument and INITIALIZED state remain.
- Lift the cap to RESUMED and verify the fragment reaches that state with exactly one onCreate.

This checks the state-saving case fixed in Fragment 1.8.8 and is retained for 1.8.9. It does not exercise real biometric hardware or prove that an actual device authentication prompt survives rotation.

## Acceptance
- Existing unit tests plus the host regression pass.
- assembleDebug, committed-schema check, lintDebug and minified assembleRelease pass on the current SDK/toolchain.
- Catalog diff changes only the Fragment version; no production Kotlin, manifest, settings or schema files change.
- Phone retest remains explicit: enable/cancel app-lock enrollment, authenticate with the device credential, rotate with a prompt open, and check cold start plus short/long background returns and Files picker flow.
- Existing screenshot-blocking defaults, recents protection and app-lock preferences remain as configured by the owner.

## Out of scope
Fragment 1.9, biometric-library upgrade, new authentication policy or screens, schema changes, other runtime/toolchain updates and signed release publication.
