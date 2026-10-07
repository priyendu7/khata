# Khata CSV format

Khata exports transactions to CSV (Settings > Export to CSV) and imports them back (Settings > Import from CSV). The export is a backup and a way to take your data to a spreadsheet. This page is the specification of the file, version **2**.

Code: `KhataCsvFormat` and `Csv` in [`feature/csv`](../feature/csv/src/main/java/com/openhand/khata/feature/csv/).

> **The file is not encrypted.** Khata's own database is, but anyone who can open the CSV file can read every transaction in it. Keep it somewhere safe.

## File

- **Encoding:** UTF-8, starting with a byte order mark (BOM, `EF BB BF`). Excel needs the BOM to show Hindi and the ₹ sign correctly when you double-click the file. Most other programs ignore it. On import, a BOM is optional.
- **Quoting:** [RFC 4180](https://www.rfc-editor.org/rfc/rfc4180). Fields are separated by commas. A field that contains a comma, a double quote or a line break is wrapped in double quotes, and a double quote inside it is written twice (`""`). Other fields are not quoted.
- **Line endings:** CRLF (`\r\n`), including after the last row. On import, LF alone also works, blank lines are skipped, and the last row may have no line break.
- **Rows:** the first line names the format and version, the second line is the header, and every line after that is one transaction, oldest first.

```
khata_csv,2
date,time,amount,direction,account,payee,payee_name,category,tags,note,reference_no,counts_in
```

### Format version

The first line is always `khata_csv,<version>`. This page describes version 2. A later version may only **add** columns at the end. It never renames, removes or reorders the columns above, so a reader for an older version can read later files by ignoring the columns it doesn't know.

| Version | Change |
| --- | --- |
| 1 | The first eleven columns, `date` to `reference_no` |
| 2 | Added `counts_in` |

On import, Khata recognises its own format by the header row: the version 1 columns, with or without the `khata_csv` line in front of it (in case a spreadsheet dropped that line). Column names are compared ignoring case. Columns added after version 1 may be missing, so version 1 files still import.

Tools such as pandas can skip the first line: `pandas.read_csv("khata.csv", skiprows=1)`.

## Columns

| Column | Required | Type and format | Empty means |
| --- | --- | --- | --- |
| `date` | Yes | Date in the phone's time zone, ISO `yyyy-MM-dd`, e.g. `2026-09-27` | Not allowed: the row is reported as invalid |
| `time` | No | 24-hour time in the phone's time zone, `HH:mm:ss`, e.g. `08:05:00`. Import also accepts `HH:mm` | Midnight (`00:00:00`) |
| `amount` | Yes | Rupees with exactly two decimals and a `.` decimal point, no ₹ sign and no digit grouping, e.g. `1250.50`. Always positive; `direction` gives the sign. Not paise. Import also accepts fewer decimals (`1250.5`, `1250`) and grouping commas | Not allowed |
| `direction` | Yes | One of `debit` (money spent), `credit` (money received), `refund` (money back for an earlier expense) or `transfer` (between your own accounts, such as paying a card bill). Import ignores case | Not allowed |
| `account` | No | The account's name, e.g. `HDFC Savings` | No account |
| `payee` | No | The payee's identifier: a UPI ID, merchant name or the name typed when the payee was first used | No payee, unless `payee_name` is set |
| `payee_name` | No | What the payee is called in Khata, e.g. `Chai stall` | Same as `payee` |
| `category` | No | The category's name as shown in the app, in the app's language when exported | Uncategorized |
| `tags` | No | Tag names separated by `\|`, e.g. `office\|snacks`. Spaces around each name are ignored | No tags |
| `note` | No | Free text; may contain commas, quotes and line breaks | No note |
| `reference_no` | No | The UPI or bank reference number | No reference number |
| `counts_in` | No | The month the transaction counts in for totals and charts, `yyyy-MM`, e.g. `2026-10` for a salary paid on 30 September for October. Exporting a date range still picks rows by `date` | The month of `date` |

Money is stored in Khata as whole paise, and the amount is converted between paise and rupees exactly, with no floating-point maths.

Times are written in the phone's time zone at the moment of export, with no offset. Importing on a phone set to a different time zone moves the transactions by the difference.

## Import rules

These rules apply to Khata's own files. Files from other apps go through a column-matching step first (below), and then follow the same rules.

- **Names:** an account, category, payee or tag in the file is matched to an existing one by name, ignoring case, or created. A new account is created as a bank account. A payee is matched by `payee` (the identifier) first, then by `payee_name`.
- **Default categories** (Food, Groceries and the rest) match by their name in English or Hindi, or by their key (`food`, `groceries`, …). So a file exported with the app in Hindi imports correctly on a phone set to English. A category the user created or renamed matches by its own name first.
- **Duplicates are skipped.** A row is a duplicate if its `reference_no` is already saved, or appears earlier in the same file. A row without a reference number is a duplicate of a saved transaction on the same day with the same direction, amount and payee (or, when there is no payee, the same note), ignoring case. Each saved transaction can match only one row, so two identical cups of tea on the same day both import the first time, and importing the same file again adds nothing.
- **Bad rows are reported, not fatal.** A row with a date, time, amount, direction or counts-in month that can't be read is listed with its row number, and the other rows still import.
- **All or nothing.** The preview shows how many rows are new, duplicate or invalid before anything is saved. Then the import runs in one database transaction, so a failure leaves nothing half imported.
- Imported transactions are marked with the source `csv`.

## Files from other apps

Any other CSV file (a bank statement, or a spreadsheet you kept by hand) is imported after matching its columns. The first row must be the column names.

- **Columns:** date (required), amount (required), description (optional; becomes the note) and category (optional). Khata guesses the columns from their names, and you can change them.
- **Date format:** `27/09/2026`, `09/27/2026`, `2026-09-27`, `27-Sep-2026` or `27/09/26`. Any of `/ - .` or a space may separate the parts, and a time after the date is ignored.
- **Spending and income**, one of:
  - one amount column where a negative amount is spending and a positive one is income;
  - one amount column where every row is spending (a hand-kept expense sheet);
  - separate spending (debit, withdrawal) and income (credit, deposit) columns. An empty or zero cell in one of them is ignored.
- **Amounts** may include `₹`, `Rs.` or `INR`, grouping commas, a minus sign before or after, brackets for a negative amount, or a `Dr` / `Cr` suffix.

## Example

Opened in a text editor (the BOM is invisible). The first row has Hindi text, a comma, quotes and a line break in the note; the second is a transfer (paying a card bill); the third has no payee, category or tags, and counts in October.

```csv
khata_csv,2
date,time,amount,direction,account,payee,payee_name,category,tags,note,reference_no,counts_in
2026-09-27,08:05:00,20.50,debit,HDFC Card,paytmqr281005050101@paytm,चाय वाला,Food,office|snacks,"Chai, samosa and ""biscuits""
for the team",426912345678,
2026-09-28,19:00:00,12500.00,transfer,HDFC Savings,,,Uncategorized,,Card bill,,
2026-09-30,09:00:00,85000.00,credit,HDFC Savings,,,,,Salary,,2026-10
```
