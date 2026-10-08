# Parser rule format (version 1)

Khata reads bank SMS with **parser rules**. A rule is data, never code: a pattern plus a few fields (PRD feature 8). The built-in banks and the rules people paste into **Settings > Parsers** use the same format and the same engine (`sms/parser`).

## Example

A Kotak UPI payment SMS:

```
Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. UPI Ref 111122223333. Not done by you? ...
```

and a rule that reads it:

```json
{
  "v": 1,
  "id": "kotak-upi-sent",
  "bank": "Kotak",
  "senders": ["KOTAKB"],
  "pattern": "Sent (?<amount>Rs\\.?[\\d,]+(?:\\.\\d{1,2})?) from Kotak Bank A/c X?(?<account>\\d{4}) to (?<payee>.+?) on (?<date>\\d{2}-\\d{2}-\\d{2})\\. ?UPI Ref:? ?(?<ref>\\d+)",
  "direction": "debit",
  "accountType": "bank",
  "dateFormat": "dd-MM-yy"
}
```

## Fields

| Field | Required | Meaning |
|---|---|---|
| `v` | yes | Format version. Always `1` for now. A rule with a newer version is refused, not half-read. |
| `id` | yes | Short unique name: lower-case letters, digits and `-`, up to 64 characters. |
| `bank` | yes | Bank name, shown to the user and used for new accounts. |
| `senders` | yes | 1–10 sender headers, such as `KOTAKB`. Phones show senders as `AX-KOTAKB-S`: the 2-letter operator prefix changes and the TRAI suffix (`-S` service, `-T` transactional, `-G` government, `-P` promotional) isn't part of the header. Which suffixes are read is up to the sender filters (see [What the engine returns](#what-the-engine-returns)); promotional senders can't be named in a rule. |
| `pattern` | yes | The regex, up to 1,000 characters. See below. |
| `direction` | one of these two | `debit`, `credit` or `refund`: the same for every SMS the rule matches. |
| `directionWords` | one of these two | Words that decide the direction, for example `{"debit": ["debited", "spent"], "credit": ["credited"]}`. They're checked in the order refund, debit, credit, against the `dir` group if the pattern has one, otherwise the whole SMS. |
| `accountType` | yes | `bank`, `credit_card`, `debit_card` or `wallet`. |
| `dateFormat` | with a `date` group | A [`java.time` pattern](https://developer.android.com/reference/java/time/format/DateTimeFormatter#patterns) for the `date` group, such as `dd-MM-yy`, `dd/MM/yyyy` or `dd-MMM-yy HH:mm`. Month names are read in English, in any case. |

Unknown fields are an error, so a typo such as `patern` is caught instead of ignored. Transfers aren't a rule direction: Khata detects them separately (card bill payments, moves between your own accounts).

## Named groups

| Group | Required | Becomes |
|---|---|---|
| `amount` | yes | The amount in paise. `Rs.1,23,456.50`, `Rs 500`, `INR 1,000.00`, `₹99` and `250` are all read; zero doesn't count as a match. |
| `payee` | no | Who was paid or who paid you (merchant, UPI ID, name). Extra spaces and trailing punctuation are removed. |
| `account` | no | The last 4 digits of the account or card: `X1234`, `XXXXXX1234` and `*1234` all give `1234`. |
| `ref` | no | The UPI or bank reference number, used to skip duplicates. |
| `date` | no | The transaction date, read with `dateFormat`. If it's the day the SMS arrived, the arrival time is kept; if it's an earlier day, that day is used with the arrival time of day. An unreadable date falls back to the arrival time. |
| `balance` | no | Read so a pattern can include it, but **never stored**. |
| `dir` | no | The part of the SMS that `directionWords` are checked against. |

Any other group name is an error.

## Pattern rules

Patterns are matched **case-insensitively**, anywhere in the SMS. Patterns run on RE2J, and only a small, predictable subset of regex syntax is allowed:

- **Allowed:** literal text, `.`, character classes like `[\d,]` and `[^.]`, `\d \w \s \b` and their capitals, `* + ? {n,m}` and their lazy forms (`+?`), groups `( )`, non-capturing groups `(?: )`, named groups `(?<name> )`, alternation `|`, `^ $`.
- **Not allowed:** lookahead and lookbehind `(?= ) (?! ) (?<= ) (?<! )`, backreferences `\1 \k<name>`, atomic groups `(?> )`, `(?P<name> )`, inline flags such as `(?i)`, possessive quantifiers such as `a*+`, the escapes `\A \Z \z \G \p \P \h \H \R \X \Q \E \C \c \u \x{…}`, and classes inside classes (`[a[b]]`, `[a&&b]`).

Remember to escape `.` in `Rs.` as `Rs\.`, and in JSON every backslash is written twice (`\\d`).

**Speed.** The app runs patterns on RE2J, where matching takes time proportional to the length of the SMS whatever the pattern, so a badly written rule can't freeze the app.

## Rule codes

To share a rule, the app turns it into a **rule code**: `khata1:` followed by the rule's JSON in unpadded base64url. Line breaks and spaces added by chat apps are ignored when pasting. When a code can't be read, the app says why:

| Code | Meaning |
|---|---|
| `bad_prefix` | Doesn't start with `khata1:` |
| `bad_base64` | The part after the prefix isn't base64url |
| `bad_json` | Not a JSON object, a field has the wrong type, or there's an unknown field |
| `unknown_version` | Missing `v`, or made for a newer version of Khata |

### Adding a code in the app

In **Settings > Parsers > Add**, paste the code. The app shows why a code can't be used (the messages for the codes above and below), and lets you test the rule on one of your recent SMS from that bank, or on one you paste, before saving. Saved rules are kept in the encrypted database and can be switched off or deleted. Pasting a rule with the same `id` as a saved one replaces it. After saving, the app offers to read the bank SMS waiting in To review with the new rule.

### Managing rules in the app

**Settings > Parsers** lists your rules first, then every built-in rule, one row per rule, grouped by bank. Each row has an on/off switch and a menu with **Edit** and **Copy code** (custom rules also have **Delete**). The engine order stays the same: custom rules first, then built-in ones.

- **Edit** opens one form for both kinds: bank name, senders, direction (fixed, or direction words), account type, date format (when the pattern has a `date` group) and, under Advanced, the pattern. **Re-mark on an SMS** opens the rule maker on a recent SMS from the rule's senders, or a pasted one, and brings the new pattern back with the same `id`. **Check** runs the edited rule over your recent SMS from its senders. Saving checks the rule with `RuleValidator`, then reads the SMS waiting in To review again.
- Built-in rules ship inside the app, so a change to one is stored in the database (`builtin_rule_overrides`) and applied on top: a rule switched off is skipped, and an edited one runs in its place, in the same position. An edit that no longer passes the checks falls back to the shipped rule. Built-in rules can't be deleted; switch them off instead. An edited one shows **Edited** and has **Reset to built-in**.
- When an app update changes a built-in rule you edited (its JSON hash differs from the one stored with your edit), the row shows **Updated version available**, with **Use new version** (drops your edit) and **Keep mine**.
- **Copy code** copies the rule's `khata1:` code (for an edited built-in rule, your version), for example to send it in to be shipped as built-in.

### Making a rule in the app

Most rules are made from an SMS on the phone rather than written by hand. Open an SMS no rule could read in **To review** and tap **Make a parser**, or go to **Settings > Parsers > Add > Make a parser** and pick one of your recent bank SMS (or paste one). Then:

1. Tap a word to select it, or tap the first and last word of a run, and say what it is: amount, payee, account, reference or date. For a date, pick its format; the common formats that read the marked date are offered.
2. Choose the direction and account type. The bank name and sender ID come from the SMS sender and can be changed.
3. The app makes the pattern (`RuleMaker` in `sms/parser`) and checks it with `RuleValidator`. **Check** shows what the rule reads from this SMS and from your other recent SMS from the same senders, or that it doesn't match them.
4. **Save** it. It's used at once, and the app offers to read the SMS waiting in To review with it. The rule code is then shown with a copy button, to share if you like.

How the pattern is made:

- Each marked part becomes its named group: the amount as `[\d,]+(?:\.\d{1,2})?` after any of `Rs.`, `Rs`, `INR` or `₹`; the payee as `.+?` (or `.+` at the very end of the SMS); the account as a mask and at least 3 digits (`X1234`, `*1234`); the reference as letters and digits; the date from its format (`dd-MMM-yy` → `\d{2}-[a-z]{3}-\d{2}`). A label inside a marked word, such as `Ref:` in `Ref:1234`, stays literal text.
- Punctuation at the ends of a marked word (the `.` in `23-09-26.`) stays literal text.
- The text between the marked parts, two words before the first and one word after the last, is literal: escaped (`.`, `+`, `*`, brackets and so on), with any run of spaces matching `\s+` and any number (a balance, a time) matching any number. Text further out, such as a helpline footer, is left out so it can change.

The SMS never leaves the phone: the app has no internet permission. To add a rule to the repo for everyone, file an issue or pull request yourself with the code and a sample SMS with personal details blanked out.

## Checks before a rule is used

Every rule, built-in, made in the app or pasted, is checked first (`RuleValidator`):

| Code | Meaning |
|---|---|
| `unknown_version` | `v` isn't `1` |
| `bad_id` | `id` has characters other than `a-z 0-9 -`, or is longer than 64 |
| `bad_bank` | `bank` is blank or longer than 40 characters |
| `bad_senders` | No senders, more than 10, a promotional (`-P`) sender, or something that isn't a sender header (such as a phone number) |
| `direction` | Neither or both of `direction` and `directionWords`, or an empty word list |
| `bad_date_format` | `dateFormat` isn't a valid pattern |
| `pattern_too_long` | Empty, or longer than 1,000 characters |
| `unsupported_pattern` | Uses syntax outside the list above, an unknown group name, or the same group twice |
| `bad_pattern` | The pattern doesn't compile |
| `missing_amount` | No `amount` group |
| `date_format_without_date` | `dateFormat` given but no `date` group |
| `date_without_format` | A `date` group but no `dateFormat` |

## What the engine returns

Every SMS goes through three steps, in this order:

1. **Sender filters**, before any text is read. They apply to every sender, including ones a rule names.
   - A phone number (or anything else that isn't a business sender ID) is never read. This isn't a switch.
   - **Drop promotional (`-P`)**, on by default.
   - **Drop government (`-G`)**, on by default.
   - **Only service senders (`-S`)**, on by default: drops `-T`, `-P` and `-G`. A sender shown with no suffix (some phones show only `HDFCBK`) passes.
2. **Rules.** The first rule (custom rules first, then built-in ones) whose sender and pattern match gives the transaction. A rule that reads an SMS always wins: the content filters never see it.
3. **Content filters**, only on an SMS no rule read. Each is a switch, on by default:
   - **Not a transaction:** an OTP, a UPI collect request, a bill or due-date reminder, a balance-only message, an e-mandate being set up, an offer, or a failed or declined payment (unless money has come back for it: "refund of", "credited back", "reversed"). Checked first, as it says the most about the SMS.
   - **No amount:** no `Rs`, `INR` or `₹` followed by a number (for example "Biometric authentication is enabled").
   - **No transaction word:** none of the words in [`TransactionWords`](../sms/parser/src/main/java/com/openhand/khata/sms/parser/TransactionWords.kt), such as debited, credited, spent, received, UPI, NEFT, txn, Dr, Cr, or Hindi डेबिट, जमा, भुगतान. Most match the start of a word ("debit" matches "debited"); short ones such as `Dr`, `Cr` and `UPI` must be whole words, so "address" and "crore" don't count.

The result is one of:

- **Parsed**: the transaction a rule read.
- **Filtered**, never stored, with the reason: `phone_number`, `promotional`, `government`, `not_service`, `no_amount`, `no_transaction_word`, or `not_transaction` with the word group that matched (`otp`, `collect_request`, `reminder`, `balance`, `mandate`, `offer`, `failed`) and the words it matched.
- **Unparsed**, anything else: it goes to the review inbox with its raw text, so a new format is noticed rather than lost. Its bank is the bank of the first rule for that sender, or none for a business no rule knows (the review card shows the sender).

Real transaction SMS often mention the not-a-transaction words ("Never share card details/OTP"), which is why content filters run only after every rule has failed. A rule, in turn, should never match a failed payment or an OTP.

## Tests

Every built-in bank has real SMS samples, with personal details replaced, in [`sms/parser/src/test/resources/samples/`](../sms/parser/src/test/resources/samples/). The test fails if fewer than 95% of a bank's samples parse correctly, or if a sample still contains something that looks like a real account, card or phone number.

The shared test vectors in [`sms/parser/src/test/resources/rule-vectors/`](../sms/parser/src/test/resources/rule-vectors/) pin down this behaviour.
