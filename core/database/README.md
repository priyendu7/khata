# `:core:database`

**Owns:** Room on SQLCipher: entities for the six PRD tables (Account, Payee, Category, Tag, Transaction, TransactionTag), DAOs, migrations and migration tests, and seeding the default categories in English and Hindi. `reference_no` is unique when present.

**Built in:** Phase 0. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Schema version 1 (done in #9):** the PRD's six tables as `accounts`, `categories`, `tags`, `payees`, `transactions` and `transaction_tags`, plus `payee_default_tags` (a payee's default tags, PRD feature 3) and `app_metadata` (key-value bookkeeping). Entities are in `entity/`, DAOs in `dao/`.

- Amounts are `amount_paise` (`Long`, always positive); `direction` (`debit`, `credit`, `refund`, `transfer`) gives the sign.
- Enums are stored as fixed lowercase text by `EnumConverters`, never as Kotlin names.
- `reference_no` has a unique index, so it's unique when present and any number of rows may have none.
- Deleting an account or category that transactions use fails (the UI must move or archive first); deleting a payee clears the link; deleting a transaction or tag removes its tag links. Tag names are unique ignoring case.
- Timestamps are epoch milliseconds (UTC).

**Default categories (done in #10):** `DefaultCategorySeeder` adds the ten PRD defaults (`DefaultCategory` in `:core:model`) when the database file is created, and again if it's recreated after a lost key. Each has a fixed `seed_key` and **no stored name**, so the UI shows it in the current language (English or Hindi) and switching languages renames it. When the user renames one, their `name` is stored and shown as typed, and the `seed_key` stays, so `uncategorized` can always be found (`CategoryDao.getBySeedKey`). Seeding uses INSERT OR IGNORE on the unique `seed_key`, so it can never duplicate.

**Changing the schema:** bump the version in `KhataDatabase`, add the migration to `KhataMigrations.ALL`, commit the new JSON from `schemas/`, and add a test in `src/androidTest/MigrationTest` that migrates real data. DAO and constraint tests run on the JVM with Robolectric (`src/test/`), so CI runs them.

**Encryption (done in #8):** `KhataDatabaseFactory` opens Room through SQLCipher's `SupportOpenHelperFactory` with the passphrase from `:core:security`. The database is opened lazily, the first time something injects it (see `DatabaseModule`), so inject it off the main thread.

**When the key is lost:** if no usable passphrase exists but a database file does, that file can never be decrypted, so it's deleted and an empty database is created; `OpenedDatabase.wasReset` tells the UI to explain this. If the key is fine but the file still won't open (corruption), nothing is deleted and the error surfaces.

**Why Room 2.8, not Room 3:** SQLCipher integrates through the SupportSQLite open-helper API that Room 2.x uses.

**Tests:** `src/test/KhataDatabaseTest` (DAOs and constraints, JVM, runs in CI); `src/androidTest/EncryptedDatabaseTest` (file isn't plain SQLite, data survives reopening, lost key or missing key file starts fresh) and `MigrationTest` (on a device: `./gradlew :core:database:connectedDebugAndroidTest`).

**Module:** `:core:database` · package `com.openhand.khata.core.database` · Android library (`khata.android.library`).
