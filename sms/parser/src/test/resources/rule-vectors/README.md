# Rule test vectors

Read by `RuleVectorsTest`, so every rule is read the same way whatever changes in the engine. The rule format is in [`docs/parser-rules.md`](../../../../../../docs/parser-rules.md). All SMS here are made up.

**Parse vectors** (every file except `invalid-rules.json`):

- `zone`: the time zone the SMS dates are read in
- `rules`: rules in the order they're tried; all must be valid
- `cases`: each has a `sender`, a `body`, `receivedAt` (ISO 8601 with offset) and `expect`:
  - `{"result": "parsed", "ruleId", "bank", "amountPaise", "direction", "accountType", "account", "payee", "ref", "timestamp"}`, where missing values are `null` and `timestamp` is compared as an instant
  - `{"result": "unparsed", "bank"}`, where `bank` is `null` for a sender no rule knows
  - `{"result": "filtered", "reason"}`, where `reason` is `phone_number`, `promotional`, `government`, `not_service`, `no_amount`, `no_transaction_word` or `not_transaction`; `not_transaction` also has a `group`: `otp`, `collect_request`, `reminder`, `balance`, `mandate`, `offer` or `failed`
- `filters` (optional, per case): switches turned off, such as `{"onlyService": false}`. Every filter is on unless a case turns it off: `dropPromotional`, `dropGovernment`, `onlyService`, `noAmount`, `noTransactionWord`, `notTransaction`

**`invalid-rules.json`:** each case has a `rule` and the `errors` codes the validator must give (an empty list means valid).
