# `:sms:parser`

**Owns:** Pure Kotlin, **no Android imports**: the declarative parser rule format, the rule engine (rule + SMS → transaction or nothing, with a time limit), built-in rules for HDFC, ICICI, Federal, Axis and Kotak, OTP/promo filtering and transfer detection. Tested against redacted SMS samples. It is a JVM-only module so the parser website can reuse it later (through Kotlin/JS).

**Built in:** Phase 3. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:sms:parser` · package `com.openhand.khata.sms.parser` · pure Kotlin/JVM (`khata.jvm.library`), no Android imports.
