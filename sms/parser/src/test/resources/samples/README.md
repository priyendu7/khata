# Bank SMS samples

Real SMS from each built-in bank, one file per bank (`kotak.json`), with personal details replaced. `SampleSmsTest` runs the built-in rules over them and fails if fewer than 95% of a bank's samples are read correctly (PRD success metric), or if a sample looks unredacted.

**Before adding a sample, replace:**

- account and card numbers: keep the mask and the last 4 digits, but change those 4 digits (`XXXXXX1234`)
- people's names, and small local shops that would show where you live
- UPI and bank reference numbers: random digits of the same length
- balances, phone numbers, and anything else personal

Keep everything else exactly as it came: spacing (even double spaces), punctuation, capital letters, amounts and dates. The layout is what the rules match.

**Format:** `bank`, `zone` (time zone the dates are read in), `note`, and `samples`, each with the `sender` as the phone shows it (`JM-KOTAKB-S`), the `body`, `receivedAt` (ISO 8601 with offset) and `expect`: either `"not_transaction"` or `{amountPaise, direction, account, payee, ref, timestamp}` with `null` for a missing value.
