# Privacy Policy — Khata

_Last updated: September 25, 2026_

Khata is free, open-source software published by priyendu7. It has no ads and is built to respect your privacy.

## The short version

- **We don't collect any personal data.** There are no accounts, no analytics, no advertising SDKs, and no trackers.
- **We don't store your data on any server.** Everything the app creates stays on your device.
- **We don't share or sell anything**, because we don't have anything to share.

## Data on your device

Khata stores your transactions, accounts (name, bank, last 4 digits only), payees, categories, tags, notes, settings and — once SMS import is available and you turn it on — the text of bank transaction SMS it recorded. All of it lives in the app's private storage on your phone, in a database encrypted with SQLCipher. The encryption key is generated on your device and kept in the Android Keystore; it never leaves the phone.

Android's automatic backup and device-to-device transfer are turned off for this app, so none of this is copied to Google Drive or another phone. The only way data leaves the app is a **CSV export that you start yourself**, saved to a location you pick; after that, the file is under your control. Uninstalling the app, or clearing its data, permanently deletes everything — there is no copy anywhere else, so export a CSV first if you want to keep your history.

## Permissions

The app only asks for permissions a feature needs, and only uses them for that feature:

| Permission | Why it's needed | Leaves your device? |
|---|---|---|
| _None yet_ | The current version requests no permissions. | — |
| Read SMS, receive SMS _(planned, milestone M3)_ | Only if you turn on SMS import: to read bank transaction messages and record them. OTPs, promotions and messages from non-bank senders are ignored. Manual entry works without it. | No — parsed on the phone only |
| Notifications _(planned)_ | Only if you turn them on: the optional daily summary and the backup reminder, generated on the phone. | No |

Khata never asks to send SMS, read your contacts, or use the internet.

## Network use

**The app does not connect to the internet.** It doesn't declare Android's `INTERNET` permission, so the operating system itself blocks it from opening network connections. Every build is checked automatically, and the build fails if any library tries to add that permission. You can confirm it in your phone's app info, which lists no network access.

The custom-parser website (planned) is a static page that runs entirely in your browser; the SMS you paste into it is never uploaded.

## Google Play

When you download the app from Google Play, Google processes data under [Google's privacy policy](https://policies.google.com/privacy). We don't receive personal data from Google beyond the aggregate, anonymised statistics every developer sees in the Play Console (e.g. install counts and crash reports that Android sends if you've allowed it).

## Children

The app doesn't collect data from anyone, including children.

## Verify it yourself

Khata is open source. You can read every line of code at https://github.com/priyendu7/khata.

## Changes and contact

If this policy ever changes, the new version will be published here with a new date. Questions: open.hand.software@gmail.com
