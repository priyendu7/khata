# `:core:security`

**Owns:** Database key generation and storage in the Android Keystore; app lock via `BiometricPrompt` with the phone's screen lock (and the optional app-only PIN with a one-time recovery code); relock timeout; `FLAG_SECURE`.

**Built in:** Phase 0 (key, lock, FLAG_SECURE); app-only PIN in a later Phase 0/1 issue. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:core:security` · package `com.openhand.khata.core.security` · Android library (`khata.android.library`).
