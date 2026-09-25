# `core.database`

**Owns:** Room on SQLCipher: entities for the six PRD tables (Account, Payee, Category, Tag, Transaction, TransactionTag), DAOs, migrations and migration tests, and seeding the default categories in English and Hindi. `reference_no` is unique when present.

**Built in:** Phase 0. See [`docs/DEVELOPMENT_PLAN.md`](../../../../../../../../docs/DEVELOPMENT_PLAN.md).

The plan moves this package into its own Gradle module (`:core:database`) during Phase 0.
