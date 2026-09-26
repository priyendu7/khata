# `:core:ui`

**Owns:** Material 3 theme with dynamic color, shared Compose components, Indian number formatting (₹1,00,000) for display only. Shared strings and icons (navigation labels, tab icons) live here; each feature module keeps its own strings in `res/values/` (English) and `res/values-hi/` (Hindi). Other modules use this module's resources as `com.openhand.khata.core.ui.R`, usually imported as `UiR`.

**Category names:** `categoryName(name, seedKey)` shows the user's name if they set one, otherwise the default category's name in the current language (`category_*` strings, English and Hindi).

**Built in:** Phase 0 (theme, navigation shell), Phase 1 (formatting). See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:core:ui` · package `com.openhand.khata.core.ui` · Android library (`khata.android.library` + `khata.android.compose`).
