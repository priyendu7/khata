# `:feature:lock`

**Owns:** the app lock UI (PRD privacy principle 4): the lock gate around the whole app, unlocking with the phone's screen lock (BiometricPrompt), the app-only PIN with its one-time recovery code, the "your phone has no screen lock" choice, and the lock section of Settings.

The rules (hashing, wrong-attempt waits, timeouts) live in `:core:security` (`core/security/.../lock/`) and are unit-tested there.

**Built in:** Phase 0 (#11). See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:feature:lock` · package `com.openhand.khata.feature.lock` · Android library (`khata.android.library` + `khata.android.compose` + `khata.android.hilt`).
