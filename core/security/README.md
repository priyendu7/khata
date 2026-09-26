# `:core:security`

**Owns:** Database key generation and storage in the Android Keystore; app lock via `BiometricPrompt` with the phone's screen lock (and the optional app-only PIN with a one-time recovery code); relock timeout; `FLAG_SECURE`.

**Built in:** Phase 0 (key, lock, FLAG_SECURE); app-only PIN in a later Phase 0/1 issue. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Database key (done in #8):** `DatabaseKeyManager` creates a random 32-byte SQLCipher passphrase on first use and stores it only wrapped (AES-256-GCM) by an Android Keystore key (`AndroidKeystoreKeyWrapper`), in `noBackupFilesDir/database-key.bin`. The plain passphrase is never written to disk or logged. If the wrapped key can't be decrypted (Keystore reset, damaged file), a new passphrase is created and `previousKeyLost` is reported so the database can start fresh.

**App lock rules (done in #11):** `lock/` holds the logic, which is plain Kotlin and unit-tested on the JVM: `LockSettings` (phone lock or app PIN, timeout, "continue without a screen lock"), `AppLockManager` (starts locked on every cold start, locks again after the timeout in the background, measured with a monotonic clock), `PinManager` (PIN of 4-12 digits and a one-time 16-character recovery code, stored only as salted PBKDF2-SHA256 hashes; after 5 wrong tries, waits of 30 s doubling to 1 h, shared by PIN and recovery-code attempts) and `SecretHasher`. The screens are in `:feature:lock`.

**Module:** `:core:security` · package `com.openhand.khata.core.security` · Android library (`khata.android.library`).
