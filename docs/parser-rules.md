# Parser rule format (version 2)

Khata reads bank SMS with **parser rules**. A rule is data, never code: a pattern plus a few fields (PRD feature 8). The built-in banks and the rules people paste into **Settings > Parsers** use the same format and the same engine (`sms/parser`).

There are two kinds of rule:

- **Transaction** rules (the default, and the only kind in version 1) read a debit, credit or refund.
- **Statement** rules (version 2) read a credit card statement: the card, the total due, the minimum due and the due date. See [Statement rules](#statement-rules-version-2).

**Versions.** Version 1 rules and codes keep working unchanged. Version 2 adds `kind`, `dueDateFormat`, the any-sender `*`, the `bank` group and the statement groups. A rule that uses none of these is still written as version 1 (by the rule maker and the editor too), so older versions of Khata can read its code. A version 1 rule that uses something from version 2 is refused with `needs_v2`.

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
| `v` | yes | Format version: `1`, or `2` for a rule that uses anything version 2 adds. A rule with a newer version is refused, not half-read. |
| `id` | yes | Short unique name: lower-case letters, digits and `-`, up to 64 characters. |
| `bank` | yes | Bank name, shown to the user and used for new accounts. |
| `kind` | no (v2) | `transaction` (the default) or `statement`. |
| `senders` | yes | 1–10 sender headers, such as `KOTAKB`, or (v2) `["*"]` for any business sender, which is tried after every rule that names the sender, so a bank's own rules always win. `*` reads service and transactional senders, never promotional or government ones. Phones show senders as `AX-KOTAKB-S`: the 2-letter operator prefix changes and the TRAI suffix (`-S` service, `-T` transactional, `-G` government, `-P` promotional) isn't part of the header. Which suffixes are read is up to the sender filters (see [What the engine returns](#what-the-engine-returns)); promotional senders can't be named in a rule. |
| `pattern` | yes | The regex, up to 1,000 characters. See below. |
| `direction` | one of these two | `debit`, `credit` or `refund`: the same for every SMS the rule matches. |
| `directionWords` | one of these two | Words that decide the direction, for example `{"debit": ["debited", "spent"], "credit": ["credited"]}`. They're checked in the order refund, debit, credit, against the `dir` group if the pattern has one, otherwise the whole SMS. |
| `accountType` | transaction rules | `bank`, `credit_card`, `debit_card` or `wallet`. A statement rule has none: a statement is always a credit card's. |
| `dateFormat` | with a `date` group | A [`java.time` pattern](https://developer.android.com/reference/java/time/format/DateTimeFormatter#patterns) for the `date` group (`statementDate` in a statement rule), such as `dd-MM-yy`, `dd/MM/yyyy` or `dd-MMM-yy HH:mm`. Month names are read in English, in any case. Optional sections read several formats: `[d-MMM-yy][d-M-yyyy]` (put a 4-digit year before a 2-digit one). |
| `dueDateFormat` | with a `dueDate` group (v2) | The same, for a statement's `dueDate` group. |

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
| `bank` | no (v2) | The bank named in the SMS, such as `ICICI` in "ICICI Bank Credit Card". Used instead of the rule's `bank` when it matches, so a rule for any sender puts a payment on the right bank's card. |

Any other group name is an error, and so is a statement group in a transaction rule.

## Statement rules (version 2)

A statement rule has `"kind": "statement"` and these groups:

| Group | Required | Becomes |
|---|---|---|
| `account` | yes | The card's last 4 digits. |
| `total` | yes | The total due, in paise. |
| `minDue` | no | The minimum due, in paise. |
| `dueDate` | yes | The due date, read with `dueDateFormat`. A statement whose due date can't be read isn't a statement. |
| `statementDate` | no | The statement date, read with `dateFormat`. |
| `bank` | no | As for transactions. |
| `balance` | no | Read so a pattern can include it, but never stored. |

It has no `amount`, `payee`, `ref`, `date`, `dir`, `direction`, `directionWords` or `accountType`. Label the amounts in the pattern ("Total due", "Min amt due") rather than relying on their order. Statement rules are tried with the others, before the content filters, so a statement is never dropped as a bill reminder.

```json
{
  "v": 2,
  "id": "hdfc-statement",
  "bank": "HDFC",
  "senders": ["HDFCBK"],
  "kind": "statement",
  "pattern": "Card XX(?<account>\\d{4}) Statement:\\s+Total due: Rs\\.(?<total>[\\d,.]+)\\s+Min\\.due: Rs\\.(?<minDue>[\\d,.]+)\\s+Pay by (?<dueDate>\\d{2}-\\d{2}-\\d{4})",
  "dueDateFormat": "dd-MM-yyyy"
}
```

Statements are saved with their credit card (#154), found by bank and last 4 digits like a transaction's account, or made when there's none. The same statement seen twice (the receiver and an inbox scan) is saved once, and links are taken out of the SMS before it's kept. A statement without a `statementDate` is dated the day it arrived. **Test a message** shows one as "Statement for card ··1234: total ₹35,299.72, minimum ₹3,450, due 23 Oct 2026".

From the statements Khata works out each card's **cycle** (the day after one statement's date to the next statement's date; the card's billing day fills in before the first statement or for a missing one) and whether each statement is **paid**: from transfers into the card after its statement date, counting a CRED payment and the card's "payment received" once when they're linked (#113).

## Generic rules

Besides one file per bank, Khata ships [`rules/generic.json`](../sms/parser/src/main/resources/rules/generic.json): rules for any sender (`"senders": ["*"]`) that read the credit card formats most Indian banks share. They're tried after every bank's own rules and every custom rule, and can be switched off or edited in **Settings > Parsers** like any built-in rule (they're listed under "Any bank").

- **Card statement** (`generic-card-statement`, and `generic-card-statement-due-first` for the due date before the amounts): the word "statement"; a card number (`XX1234`, `ending with 1234`, `no. XX1234`) after "<Bank> Bank Credit Card"; an amount labelled total ("Total due", "Total amt", "Total of"); one labelled minimum ("Min.due", "Min amt due", "minimum of"); and a due date after "due by", "Due on" or "Pay by". Amounts may have `Dr.`, `INR`, `Rs` or `Rs.`, double spaces and commas.
- **Card payment received** (`generic-card-payment`, and `generic-card-payment-greeting` for "Dear <Bank> Cardmember, payment of …"): "payment of <amount> … received … credit card … (XX1234 / ending with 1234) … on <date>". It's a credit to the card, which bill-payment pairing (#56, #113) turns into the card side of a transfer. The available limit is never read as the amount.

## Pattern rules

Patterns are matched **case-insensitively**, anywhere in the SMS. Patterns run on RE2J, and only a small, predictable subset of regex syntax is allowed:

- **Allowed:** literal text, `.`, character classes like `[\d,]` and `[^.]`, `\d \w \s \b` and their capitals, `* + ? {n,m}` and their lazy forms (`+?`), groups `( )`, non-capturing groups `(?: )`, named groups `(?<name> )`, alternation `|`, `^ $`.
- **Not allowed:** lookahead and lookbehind `(?= ) (?! ) (?<= ) (?<! )`, backreferences `\1 \k<name>`, atomic groups `(?> )`, `(?P<name> )`, inline flags such as `(?i)`, possessive quantifiers such as `a*+`, the escapes `\A \Z \z \G \p \P \h \H \R \X \Q \E \C \c \u \x{…}`, and classes inside classes (`[a[b]]`, `[a&&b]`).

Remember to escape `.` in `Rs.` as `Rs\.`, and in JSON every backslash is written twice (`\\d`).

**Speed.** The app runs patterns on RE2J, where matching takes time proportional to the length of the SMS whatever the pattern, so a badly written rule can't freeze the app.

## Rule codes

To share a rule, the app turns it into a **rule code**: `khata1:` followed by the rule's JSON in unpadded base64url. The prefix is the same for version 1 and 2 rules: the `v` inside says which, so an older Khata given a version 2 code says it's for a newer version. Line breaks and spaces added by chat apps are ignored when pasting. When a code can't be read, the app says why:

| Code | Meaning |
|---|---|
| `bad_prefix` | Doesn't start with `khata1:` |
| `bad_base64` | The part after the prefix isn't base64url |
| `bad_json` | Not a JSON object, a field has the wrong type, or there's an unknown field |
| `unknown_version` | Missing `v`, or made for a newer version of Khata (anything above `2`) |

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

1. Say what the SMS is: a **transaction** or a **card statement**. Khata guesses, and changing it guesses the fields again.
2. Tap a word to select it, or tap the first and last word of a run, and say what it is: amount, payee, account, reference or date (for a statement: card, total due, minimum due and due date, told apart by their labels). For a date, pick its format; the common formats that read the marked date are offered.
3. Choose the direction and account type (a statement has neither). The bank name and sender ID come from the SMS sender and can be changed.
4. The app makes the pattern (`RuleMaker` in `sms/parser`) and checks it with `RuleValidator`. **Check** shows what the rule reads from this SMS and from your other recent SMS from the same senders, or that it doesn't match them.
5. **Save** it. It's used at once, and the app offers to read the SMS waiting in To review with it. The rule code is then shown with a copy button, to share if you like.

How the pattern is made:

- Each marked part becomes its named group: the amount as `[\d,]+(?:\.\d{1,2})?` after any of `Rs.`, `Rs`, `INR` or `₹`; the payee as `.+?` (or `.+` at the very end of the SMS); the account as a mask and at least 3 digits (`X1234`, `*1234`); the reference as letters and digits; the date from its format (`dd-MMM-yy` → `\d{2}-[a-z]{3}-\d{2}`). A label inside a marked word, such as `Ref:` in `Ref:1234`, stays literal text.
- Punctuation at the ends of a marked word (the `.` in `23-09-26.`) stays literal text.
- The text between the marked parts, two words before the first and one word after the last, is literal: escaped (`.`, `+`, `*`, brackets and so on), with any run of spaces matching `\s+` and any number (a balance, a time) matching any number. Text further out, such as a helpline footer, is left out so it can change.

The SMS never leaves the phone: the app has no internet permission. To add a rule to the repo for everyone, file an issue or pull request yourself with the code and a sample SMS with personal details blanked out.

## Checks before a rule is used

Every rule, built-in, made in the app or pasted, is checked first (`RuleValidator`):

| Code | Meaning |
|---|---|
| `unknown_version` | `v` isn't `1` or `2` |
| `needs_v2` | A version 1 rule uses something only version 2 has: `kind`, `dueDateFormat`, the `*` sender, or a `bank` or statement group |
| `not_for_kind` | A field or group the rule's kind doesn't have: `amount`, `direction` or `accountType` in a statement rule, or `total` or `dueDateFormat` in a transaction rule |
| `missing_account_type` | A transaction rule has no `accountType` |
| `missing_card` | A statement rule has no `account` group |
| `missing_total` | A statement rule has no `total` group |
| `missing_due_date` | A statement rule has no `dueDate` group |
| `due_date_without_format` | A `dueDate` group but no `dueDateFormat` |
| `due_date_format_without_due_date` | `dueDateFormat` given but no `dueDate` group |
| `bad_id` | `id` has characters other than `a-z 0-9 -`, or is longer than 64 |
| `bad_bank` | `bank` is blank or longer than 40 characters |
| `bad_senders` | No senders, more than 10, a promotional (`-P`) sender, something that isn't a sender header (such as a phone number), or `*` alongside other senders |
| `direction` | Neither or both of `direction` and `directionWords`, or an empty word list |
| `bad_date_format` | `dateFormat` isn't a valid pattern |
| `pattern_too_long` | Empty, or longer than 1,000 characters |
| `unsupported_pattern` | Uses syntax outside the list above, an unknown group name, or the same group twice |
| `bad_pattern` | The pattern doesn't compile |
| `missing_amount` | A transaction rule has no `amount` group |
| `date_format_without_date` | `dateFormat` given but no `date` group (`statementDate` in a statement rule) |
| `date_without_format` | A `date` (or `statementDate`) group but no `dateFormat` |

## What the engine returns

Every SMS goes through three steps, in this order:

1. **Sender filters**, before any text is read. They apply to every sender, including ones a rule names.
   - A phone number (or anything else that isn't a business sender ID) is never read. This isn't a switch.
   - **Drop promotional (`-P`)**, on by default.
   - **Drop government (`-G`)**, on by default.
   - **Only service senders (`-S`)**, on by default: drops `-T`, `-P` and `-G`. A sender shown with no suffix (some phones show only `HDFCBK`) passes.
2. **Rules.** The first rule (custom rules first, then built-in ones, then rules for any sender) whose sender and pattern match gives the transaction or the statement. A rule that reads an SMS always wins: the content filters never see it.
3. **Content filters**, only on an SMS no rule read. Each is a switch, on by default:
   - **Not a transaction:** an OTP, a UPI collect request, a bill or due-date reminder, a balance-only message, an e-mandate being set up, an offer, or a failed or declined payment (unless money has come back for it: "refund of", "credited back", "reversed"). Checked first, as it says the most about the SMS.
   - **No amount:** no `Rs`, `INR` or `₹` followed by a number (for example "Biometric authentication is enabled").
   - **No transaction word:** none of the words in [`TransactionWords`](../sms/parser/src/main/java/com/openhand/khata/sms/parser/TransactionWords.kt), such as debited, credited, spent, received, UPI, NEFT, txn, Dr, Cr, or Hindi डेबिट, जमा, भुगतान. Most match the start of a word ("debit" matches "debited"); short ones such as `Dr`, `Cr` and `UPI` must be whole words, so "address" and "crore" don't count.

The result is one of:

- **Parsed**: the transaction a rule read.
- **Statement**: the credit card statement a statement rule read (bank, card, total, minimum, due date, statement date).
- **Filtered**, never stored, with the reason: `phone_number`, `promotional`, `government`, `not_service`, `no_amount`, `no_transaction_word`, or `not_transaction` with the word group that matched (`otp`, `collect_request`, `reminder`, `balance`, `mandate`, `offer`, `failed`) and the words it matched.
- **Unparsed**, anything else: it goes to the review inbox with its raw text, so a new format is noticed rather than lost. Its bank is the bank of the first rule that names that sender, or none for a business no rule names (the review card shows the sender).

Real transaction SMS often mention the not-a-transaction words ("Never share card details/OTP"), which is why content filters run only after every rule has failed. A rule, in turn, should never match a failed payment or an OTP.

## Tests

Every built-in bank (and the generic rules) has real SMS samples, with personal details replaced, in [`sms/parser/src/test/resources/samples/`](../sms/parser/src/test/resources/samples/). The test fails if fewer than 95% of a bank's samples parse correctly, or if a sample still contains something that looks like a real account, card or phone number.

The shared test vectors in [`sms/parser/src/test/resources/rule-vectors/`](../sms/parser/src/test/resources/rule-vectors/) pin down this behaviour.
