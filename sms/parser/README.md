# `:sms:parser`

**Owns:** Pure Kotlin, **no Android imports**: the declarative parser rule format ([`docs/parser-rules.md`](../../docs/parser-rules.md)), the rule engine (`SmsParser`: rule + SMS → transaction or a reason there isn't one), the rule checks (`RuleValidator`) and rule codes (`RuleCode`), built-in rules (Kotak for now; other banks through custom parsers until they're added here), OTP/promo filtering and transfer detection. Patterns run on RE2J, which matches in linear time, so no rule can freeze the app. Built-in rules are JSON in `src/main/resources/rules/` (`BuiltInRules`). Tested against real, redacted SMS in `src/test/resources/samples/` (95% per bank, and a check that they're redacted) and the shared vectors in `src/test/resources/rule-vectors/`.

**Built in:** Phase 3. See [`docs/DEVELOPMENT_PLAN.md`](../../docs/DEVELOPMENT_PLAN.md).

**Module:** `:sms:parser` · package `com.openhand.khata.sms.parser` · pure Kotlin/JVM (`khata.jvm.library`), no Android imports.
