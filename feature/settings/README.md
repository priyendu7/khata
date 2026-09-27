# `:feature:settings`

**Owns:** App lock and timeout, language (per-app locale, English/Hindi), backup reminder interval (WorkManager), SMS import toggle, and Settings > Parsers for custom rules.

**Backup reminder (#45):** `BackupReminderSection` turns the reminder on or off and sets its interval (7, 14, 30 or 60 days). Turning it on asks for the notification permission on Android 13+; if that's refused, it says the reminder shows on Home only.

**Built in:** Phase 1 (language), Phase 2 (backup reminder), Phase 3 (SMS, parsers). See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:feature:settings` · package `com.openhand.khata.feature.settings` · Android library (`khata.android.library` + `khata.android.compose` + `khata.android.hilt`).
