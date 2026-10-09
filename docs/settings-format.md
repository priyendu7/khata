# Khata settings file format

Khata saves its settings to one password-protected file (Settings > Export settings) and brings them back (Settings > Import settings). The file holds what the [CSV backup](csv-format.md) doesn't: parser rules, filters, setup data and preferences. Restoring a phone is then: import settings, then import transactions. Both match by name, so the other order works too.

This page is the specification of the file, format version **1**. Code: `SettingsCrypto`, `SettingsFile` and `SettingsBackupRepository` in [`core/data`](../core/data/src/main/java/com/openhand/khata/core/data/), and `AppPreferences` in [`feature/settings`](../feature/settings/src/main/java/com/openhand/khata/feature/settings/).

**Never in the file:**
- the app PIN or recovery code (or their hashes);
- the database key;
- transactions, which are the CSV's job;
- state such as the last SMS scan, the last export time or one-off migration flags.

The suggested name is `khata-settings-YYYY-MM-DD.khata`.

## Envelope

The file is binary. All numbers are big-endian.

| Offset | Bytes | Content |
| --- | --- | --- |
| 0 | 8 | Magic: ASCII `KHATASET` |
| 8 | 1 | Format version: `1` |
| 9 | 1 | Key derivation: `1` = PBKDF2-HMAC-SHA256 |
| 10 | 4 | PBKDF2 iterations (Khata writes 600,000) |
| 14 | 16 | Random salt |
| 30 | 12 | Random IV (nonce) |
| 42 | rest | AES-256-GCM ciphertext followed by its 128-bit tag |

- The key is PBKDF2-HMAC-SHA256 of the password (UTF-16 chars, as `javax.crypto` takes them) with the salt and iteration count above, 256 bits long. It's the same PBKDF2 step that hashes the app PIN.
- The 42 header bytes are the GCM **associated data**, so changing any of them, including the iteration count, makes decryption fail.
- Every export uses a fresh salt and IV, so two exports of the same settings never look alike.
- A wrong password and a changed or cut-short file fail the same way (the GCM tag doesn't match). Khata says "Wrong password, or the file is damaged" and changes nothing.
- A format version above 1 is refused with "made by a newer Khata". Iteration counts above 10,000,000 are refused as damaged.

Only `javax.crypto` is used, so there's no extra library.

## Contents

Decrypted, the file is UTF-8 JSON. Every field is optional on import, so a file from an older Khata still reads. Fields this version doesn't know are ignored. A null or missing value means "not in the file", and that setting is left as it is.

```json
{
  "formatVersion": 1,
  "exportedAt": "2026-10-09T08:30:00Z",
  "appVersion": "0.9.0",
  "customParsers": [{ "code": "khata1:…", "enabled": true }],
  "builtInOverrides": [
    { "ruleId": "kotak-upi-sent", "enabled": true, "editedCode": "khata1:…", "baseHash": "3f2a…" }
  ],
  "ignoreRules": [
    { "kind": "sender", "header": "OFFERS", "sample": "Win a prize…", "enabled": true },
    { "kind": "template", "header": "HDFCBK", "pattern": "…", "sample": "…", "enabled": true }
  ],
  "smsFilters": {
    "dropPromotional": true, "dropGovernment": true, "onlyService": true,
    "noAmount": true, "noTransactionWord": true, "notTransaction": true
  },
  "preferences": {
    "smsImport": true, "backupReminder": true, "backupReminderDays": 30, "language": "hi",
    "appLock": true, "lockMethod": "device", "lockTimeout": "1m", "blockScreenshots": true
  },
  "categories": [
    { "seedKey": "food", "name": "Khana", "color": "#FFE65100", "icon": "food", "archived": false },
    { "name": "Pets", "color": "#FF445566", "icon": "pets", "archived": false }
  ],
  "accounts": [{ "name": "HDFC card", "type": "credit_card", "bank": "HDFC", "last4": "4321" }],
  "payees": [
    {
      "identifier": "vet@upi", "displayName": "Vet", "defaultCategory": { "name": "Pets" },
      "defaultTags": ["dog"], "ownAccount": false
    }
  ],
  "events": [{ "name": "Goa trip", "firstDay": "2026-10-01", "lastDay": "2026-10-05" }]
}
```

| Field | Meaning |
| --- | --- |
| `customParsers` | Rule codes from Settings > Parsers ([parser-rules.md](parser-rules.md)), newest first, each switched on or off |
| `builtInOverrides` | Changes to built-in rules (#111): switched off, and/or `editedCode` with the `baseHash` of the built-in rule it was edited from |
| `ignoreRules` | `kind` is `sender` or `template`; `pattern` only for `template` |
| `smsFilters` | The six filter switches in Settings > SMS import > Filters |
| `preferences.smsImport` | Whether SMS import was on. Import never turns it on, because that needs the SMS permission; it offers "Turn on SMS import" instead |
| `preferences.language` | `system`, `en` or `hi` |
| `preferences.lockMethod` | `device` or `pin`. `pin` on a phone with no app PIN keeps the phone's lock and offers to set a PIN |
| `preferences.lockTimeout` | `immediately`, `30s`, `1m` or `5m` |
| `categories` | `seedKey` for the default categories, `name` when the user renamed or made one; `color` is `#AARRGGBB` |
| `accounts` | `type` is `bank`, `credit_card`, `debit_card` or `wallet`; only ever the last 4 digits |
| `payees` | `defaultCategory` refers to a category by `seedKey` and/or `name` |
| `events` | Local dates, both included |

## Import rules

- **Preview first.** Nothing is saved until the user taps Import. Then everything in the database is saved in one transaction, so a failure leaves nothing half imported. Preferences are applied after that.
- **Custom parsers** are matched by the rule id inside the code. A new one is added, on or off as in the file. One that's different here is replaced or kept, as the user chooses for each rule. A code this version can't read or check is skipped.
- **Built-in rule changes** work the same way. A change for a built-in rule this version doesn't have is skipped and listed. An edit made from another version of the built-in rule keeps its `baseHash`, so Settings > Parsers shows "Updated version available" for it.
- **Ignore rules:** new ones are added. One for the same sender, or with the same pattern, is a duplicate and skipped.
- **Setup data** is matched like CSV import, ignoring case:
  - categories by `seedKey`, then by name;
  - accounts by name;
  - payees by identifier, then by name;
  - events by name.

  New ones are added. Matching ones take the file's details: colour, icon and archived; type, bank and last 4 digits; display name, default category, default tags (created as needed) and own account; an event's dates, moving its tag to the transactions in the new dates.
- Afterwards, SMS waiting in To review are read again with the new rules.
