# `:sms:ingest`

**Owns:** The new-SMS `BroadcastReceiver` and the one-time inbox scan from a user-chosen date. Each parsed SMS is saved through `SmsImporter` in `:core:data`, which finds or creates the account, applies payee memory and skips duplicates (reference number, the same SMS text, else amount + direction + account within 2 minutes). `READ_SMS`/`RECEIVE_SMS` and the receiver's manifest entry are added here — not before — because Play blocks uploads that declare them until the SMS declaration is approved.

**Built in:** Phase 3. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:sms:ingest` · package `com.openhand.khata.sms.ingest` · Android library (`khata.android.library`).
