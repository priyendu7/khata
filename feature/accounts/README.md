# `:feature:accounts`

**Owns:** the Accounts screen (Settings → Accounts): bank accounts, credit cards, debit cards and wallets, with name, bank and the last 4 digits (never more). Deleting an account that has transactions moves them to another account or to "no account".

The rules are in `AccountRepository` (`:core:data`) and tested there.

**Built in:** Phase 1 (#17). See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:feature:accounts` · package `com.openhand.khata.feature.accounts` · Android library (`khata.android.library` + `khata.android.compose` + `khata.android.hilt`).
