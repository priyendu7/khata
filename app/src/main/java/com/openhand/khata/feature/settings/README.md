# `feature.settings`

**Owns:** App lock and timeout, language (per-app locale, English/Hindi), backup reminder interval (WorkManager), SMS import toggle, and Settings > Parsers for custom rules.

**Built in:** Phase 1 (language), Phase 2 (backup reminder), Phase 3 (SMS, parsers). See [`docs/DEVELOPMENT_PLAN.md`](../../../../../../../../../docs/DEVELOPMENT_PLAN.md).

The plan moves this package into its own Gradle module (`:feature:settings`) during Phase 0.
