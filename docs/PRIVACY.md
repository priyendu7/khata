# Privacy Policy — Khata

_Last updated: September 25, 2026_

Khata is free, open-source software published by priyendu7. It has no ads and is built to respect your privacy.

## The short version

- **We don't collect any personal data.** There are no accounts, no analytics, no advertising SDKs, and no trackers.
- **We don't store your data on any server.** Everything the app creates stays on your device.
- **We don't share or sell anything**, because we don't have anything to share.

## Data on your device

Khata stores your transactions, accounts (name, bank, last 4 digits only), payees, categories, tags, notes, settings and — if you turn on SMS import — the text of the transaction SMS it recorded, and of SMS from businesses it couldn't read until you add them by hand or dismiss them (dismissed ones are deleted). Parser rules you add in Settings > Parsers are kept there too, and so are ignore rules ("ignore this sender", "ignore messages like this"), each with the SMS it was made from, until you delete them in Settings > SMS import > Filters. All of it lives in the app's private storage on your phone, in a database encrypted with SQLCipher. Its key is a random key generated on your phone, stored only in encrypted form, and that encryption uses a second key kept in the Android Keystore (your phone's secure key storage), which can't be copied off the device. No key ever leaves the phone.

By default Khata locks itself: it opens behind your phone's fingerprint, face, PIN or pattern (or an app-only PIN if you choose one), locks again after a time you choose in the background, and hides its screen in the recent-apps view and blocks screenshots. You can turn the lock and the screenshot blocking off in Settings. An app PIN and its recovery code are stored only as one-way hashes.

If the phone's secure key storage is ever reset (for example after some factory resets or security updates), the database can no longer be decrypted by anyone, including you. Khata then starts with an empty database instead of failing to open. This is another reason to export a CSV backup regularly.

Android's automatic backup and device-to-device transfer are turned off for this app, so none of this is copied to Google Drive or another phone. The only way data leaves the app is a **CSV export that you start yourself**, saved to a location you pick; after that, the file is under your control. Uninstalling the app, or clearing its data, permanently deletes everything — there is no copy anywhere else, so export a CSV first if you want to keep your history.

## Permissions

The app only asks for permissions a feature needs, and only uses them for that feature:

| Permission | Why it's needed | Leaves your device? |
|---|---|---|
| Use biometrics / use fingerprint | To unlock Khata with your fingerprint or face through Android's own unlock prompt (the app lock). Khata never sees your fingerprint or face; Android only tells it whether unlocking succeeded. No prompt is shown for this permission. | No |
| Read SMS, receive SMS | Asked for only when you turn on SMS import (Settings > SMS import): to read transaction messages from businesses (banks, wallets, shops: senders with an ID like AX-HDFCBK-S) and record them. Messages from people (phone numbers) are never read, and by default neither are promotional, government or non-service senders. OTPs, offers, reminders and messages with no amount or no transaction word are filtered out and not stored. Only the filter switches and import counts are kept outside the encrypted database, never SMS text. You can turn it off at any time, and manual entry works without it. | No — parsed on the phone only |
| Notifications | Asked for only when you turn on the backup reminder (and, later, the optional daily summary). The reminder says only that it's time to export a backup, never any amounts or transactions. If you refuse, the reminder shows inside the app instead. | No |
| Run at startup, prevent phone from sleeping | So the once-a-day backup-reminder check can run briefly and is rescheduled after the phone restarts. No prompt is shown for these. | No |

Khata never asks to send SMS, read your contacts, or use the internet.

## Network use

**The app does not connect to the internet.** It doesn't declare Android's `INTERNET` permission, so the operating system itself blocks it from opening network connections. Every build is checked automatically, and the build fails if any library tries to add that permission. You can confirm it in your phone's app info, which lists no network access.

Custom parsers are made inside the app from SMS already on your phone, so those SMS are never uploaded anywhere.

## Google Play

When you download the app from Google Play, Google processes data under [Google's privacy policy](https://policies.google.com/privacy). We don't receive personal data from Google beyond the aggregate, anonymised statistics every developer sees in the Play Console (e.g. install counts and crash reports that Android sends if you've allowed it).

## Children

The app doesn't collect data from anyone, including children.

## Verify it yourself

Khata is open source. You can read every line of code at https://github.com/priyendu7/khata.

## Changes and contact

If this policy ever changes, the new version will be published here with a new date. Questions: open.hand.software@gmail.com
