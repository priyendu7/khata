# `sms.parser`

**Owns:** Pure Kotlin, **no Android imports**: the declarative parser rule format, the rule engine (rule + SMS → transaction or nothing, with a time limit), built-in rules for HDFC, ICICI, Federal, Axis and Kotak, OTP/promo filtering and transfer detection. Tested against redacted SMS samples. Will become its own JVM module so the parser website can reuse it.

**Built in:** Phase 3. See [`docs/DEVELOPMENT_PLAN.md`](../../../../../../../../../docs/DEVELOPMENT_PLAN.md).

The plan moves this package into its own Gradle module (`:sms:parser`) during Phase 0.
