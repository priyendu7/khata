# `:feature:csv`

**Owns:** CSV export (all or a date range) and import (Khata's format, or any CSV via column matching) through the Storage Access Framework — no storage permissions. Duplicate detection. Format documented in `docs/csv-format.md`.

**Done in #44:**

- `Csv`: RFC 4180 reading and writing in plain Kotlin (no library).
- `KhataCsvFormat`: Khata's own format, specified in [`docs/csv-format.md`](../../docs/csv-format.md).
- `ColumnMapping`: CSV files from other apps. It guesses the columns and date format, and reads signed, all-spending or separate debit/credit amounts.
- `ExportScreen` and `ImportScreen` with their ViewModels. Import goes: pick a file, match the columns (other apps only), preview the counts of new, duplicate and invalid rows, then import.
- Files are read and written through `CsvFiles` (Storage Access Framework).
- Names, duplicate checks and the single-transaction import are in `BackupRepository` (`:core:data`).
- `CategoryNames` writes default categories in the app's language and matches them in English or Hindi on import.
- Tests: codec, format and column matching on the JVM; export-then-import round trips on an in-memory database; Compose screen tests.

**Built in:** Phase 2. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:feature:csv` · package `com.openhand.khata.feature.csv` · Android library (`khata.android.library` + `khata.android.compose` + `khata.android.hilt`).
