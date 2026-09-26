# Khata

[![CI](https://github.com/priyendu7/khata/actions/workflows/ci.yml/badge.svg)](https://github.com/priyendu7/khata/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/priyendu7/khata?include_prereleases)](https://github.com/priyendu7/khata/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

A private, offline expense tracker that turns your bank SMS into a categorized spending diary.

**Free. No ads. No data collection.** Khata doesn't have accounts, analytics, or trackers, and the app does not even have permission to use the internet — CI fails the build if anything adds it. See [`docs/PRIVACY.md`](docs/PRIVACY.md).

> **Status:** Pre-MVP / early development. See [`docs/PRD.md`](docs/PRD.md) and [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) for the full product and engineering plan.

## Why

Popular Indian expense apps need an account, show ads, and upload your SMS and spending data to their servers. People who care about privacy fall back to hand-kept spreadsheets and lose track. Khata turns the transaction SMS your bank already sends into a categorized expense diary with charts — entirely on your phone, in an encrypted database, behind your phone's own lock. It's for salaried people in India who pay mostly by UPI and card and want their spending tracked without handing their data to anyone.

## MVP scope

1. **Transactions** — add, edit and delete income, expenses, refunds and transfers; transfers (card bill payments, moving money between your own accounts) never count as spending.
2. **Categories and tags** — one category and any number of tags per transaction; filter by category, tag, account and date range.
3. **Payee memory** — name a payee and pick its category once; later transactions are filled in automatically.
4. **Review inbox** — new payees wait in a "To review" inbox, handled one card at a time.
5. **Charts** — category donut, 12-month calendar heatmap, month-by-month comparison, and a home summary.
6. **CSV export and import** — your backup, and a way in from existing spreadsheets.
7. **SMS reading** — on-device parsers for HDFC, ICICI, Federal, Axis and Kotak (milestone M3).
8. **Custom parsers** — add a bank yourself with a declarative rule built on the in-browser parser website (M3).

Everything is protected by an app lock and an encrypted database, in English and Hindi. **Out of scope for v1:** iOS, cloud sync or accounts, bill reminders, investments, reading PDF statements or email, any analytics/crash-reporting/ads SDK, and F-Droid.

## Tech stack

| Layer | Choice |
|---|---|
| Language/UI | Kotlin + Jetpack Compose |
| Design | Material 3 with dynamic color; English and Hindi strings |
| Architecture | MVVM, Hilt for wiring (Gradle module split planned in Phase 0) |
| Storage | Room on SQLCipher; key generated on-device and kept in the Android Keystore |
| Charts | Vico (donut, bars); custom Compose Canvas for the calendar heatmap |
| Security | `androidx.biometric` app lock, `FLAG_SECURE`, Android backup disabled |
| Background work | WorkManager (daily summary, backup reminder) |
| SMS | `BroadcastReceiver` + one-time inbox scan; parsers are pure Kotlin |
| CSV | Storage Access Framework (system file picker) |
| Network | **None** — no `INTERNET` permission, enforced in CI |
| Min / target SDK | 26 (Android 8.0) / 36 |

## Project layout

```
app/                     Entry point: KhataApp, MainActivity, bottom navigation, launcher icon
core/
├── model/               Plain Kotlin data classes (Transaction, Payee…) — JVM only
├── database/            Room + SQLCipher, DAOs, migrations
├── data/                Repositories
├── security/            Keystore key, device-lock / biometric helpers
└── ui/                  Theme, shared Compose components, shared strings and icons
feature/
├── transactions/        List, add/edit, review inbox
├── payees/              Payee memory screens
├── categories/          Categories and tags
├── insights/            Home summary, donut, heatmap, monthly chart
├── csv/                 Import and export
└── settings/            Lock, backup reminder, parsers, language
sms/
├── parser/              Bank parsers + rule engine — JVM only, no Android deps
└── ingest/              SMS receiver, inbox scan, dedupe
build-logic/             Convention plugins shared by every module
gradle/libs.versions.toml  All plugin and library versions
```

Each module has a README saying what it owns and which milestone builds it. Every module uses the
`com.openhand.khata.<module>` package. `parser-web/` (the custom-parser website) arrives in Phase 3.

## Getting started

Requires JDK 17+ and the Android SDK (API 36). The app is currently a navigation shell with empty screens; most modules are still stubs.

```bash
git clone https://github.com/priyendu7/khata.git
cd khata
./gradlew assembleDebug
```

Run the same checks CI runs on every PR:

```bash
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug
.github/scripts/check-no-internet.sh
```

`./gradlew ktlintFormat` auto-fixes most style issues. Builds are distributed through Google Play testing tracks (internal → closed → open → production); signed APKs are also attached to each [GitHub release](https://github.com/priyendu7/khata/releases). See [`docs/RELEASING.md`](docs/RELEASING.md). Pull requests can be tested from the Play Store before merge via the separate Khata QA app — see [`docs/TESTING_ON_PLAY.md`](docs/TESTING_ON_PLAY.md).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for branch/commit conventions and how the development plan maps to issues. Please read [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) as well.

## License

[GNU GPL v3](LICENSE) — you may use, study, share and modify Khata; derived versions must stay open under the same license.
