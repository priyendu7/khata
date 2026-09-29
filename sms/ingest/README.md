# `:sms:ingest`

**Owns:** The new-SMS `BroadcastReceiver` (`SmsReceiver`: declared disabled, enabled with the Settings switch, protected by `BROADCAST_SMS`, saves in the receiver with `goAsync` so SMS text never goes through WorkManager's unencrypted database) and the inbox import from a user-chosen date (`InboxScanner`, run by `SmsScanWorker`). Both check the sender before reading the text, and share `SmsIngestor`. Each parsed SMS is saved through `SmsImporter` in `:core:data`, which finds or creates the account, applies payee memory and skips duplicates (reference number, the same SMS text, else amount + direction + account within 2 minutes). The receiver's manifest entry is here; `READ_SMS`/`RECEIVE_SMS` are declared by `:app` (#50). No release goes to a Play track until the SMS declaration is approved (docs/RELEASING.md).

**Built in:** Phase 3. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:sms:ingest` · package `com.openhand.khata.sms.ingest` · Android library (`khata.android.library`).
