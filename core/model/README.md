# `:core:model`

**Owns:** Plain Kotlin data classes shared by every layer: `Transaction`, `Account`, `Payee`, `Category`, `Tag`, `Direction` (debit / credit / refund / transfer). Amounts are always `Long` paise. No Android or Room imports here.

**Built in:** Phase 0 (schema), grown in Phase 1. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:core:model` · package `com.openhand.khata.core.model` · pure Kotlin/JVM (`khata.jvm.library`).
