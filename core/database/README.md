# `:core:database`

**Owns:** Room on SQLCipher: entities for the six PRD tables (Account, Payee, Category, Tag, Transaction, TransactionTag), DAOs, migrations and migration tests, and seeding the default categories in English and Hindi. `reference_no` is unique when present.

**Built in:** Phase 0. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Encryption (done in #8):** `KhataDatabaseFactory` opens Room through SQLCipher's `SupportOpenHelperFactory` with the passphrase from `:core:security`. The database is opened lazily, the first time something injects it (see `DatabaseModule`), so inject it off the main thread.

**When the key is lost:** if no usable passphrase exists but a database file does, that file can never be decrypted, so it's deleted and an empty database is created; `OpenedDatabase.wasReset` tells the UI to explain this. If the key is fine but the file still won't open (corruption), nothing is deleted and the error surfaces.

**Why Room 2.8, not Room 3:** SQLCipher integrates through the SupportSQLite open-helper API that Room 2.x uses.

**Tests:** `src/androidTest/EncryptedDatabaseTest` (file isn't plain SQLite, data survives reopening, lost key or missing key file starts fresh). Run with `./gradlew :core:database:connectedDebugAndroidTest`.

**Module:** `:core:database` · package `com.openhand.khata.core.database` · Android library (`khata.android.library`).
