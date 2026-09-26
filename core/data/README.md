# `:core:data`

**Owns:** Repositories that ViewModels talk to. Owns business rules such as "transfers are excluded from spending and income totals" and payee-memory lookup.

**Done in #17:** `AccountRepository`, `CategoryRepository` and `TagRepository`, with Robolectric tests in `src/test/`. Repositories get the database as `dagger.Lazy<KhataDatabase>` and only touch it on the IO dispatcher (`DatabaseAccess.kt`), because opening it does Keystore and disk work.

**Built in:** Phase 1. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:core:data` · package `com.openhand.khata.core.data` · Android library (`khata.android.library`).
