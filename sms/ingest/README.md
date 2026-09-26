# `:sms:ingest`

**Owns:** The new-SMS `BroadcastReceiver`, the one-time inbox scan from a user-chosen date, and dedupe (reference number, else amount + time + account). `READ_SMS`/`RECEIVE_SMS` and the receiver's manifest entry are added here — not before — because Play blocks uploads that declare them until the SMS declaration is approved.

**Built in:** Phase 3. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:sms:ingest` · package `com.openhand.khata.sms.ingest` · Android library (`khata.android.library`).
