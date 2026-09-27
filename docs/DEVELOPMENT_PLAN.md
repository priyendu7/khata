# Khata Development Plan

2026-09-25 · SLPS

## Summary

The plan takes Khata from an empty repo to a Play Store release in five phases. It follows the milestones in the PRD. Every phase ends with a working app you can install, not just finished code. There are no deadlines: each phase starts when the one before it is done.

| Phase | Result you can see |
| --- | --- |
| 0 Foundation | The app opens behind the phone lock; CI is green and blocks the INTERNET permission |
| 1 Manual tracking | Add income and expenses by hand, in English or Hindi, with payee memory |
| 2 Charts and CSV | Pie, calendar heatmap and monthly chart; import your spreadsheet |
| 3 SMS and custom parsers | Your bank SMS are recorded automatically; the parser website is live |
| 4 Release | Closed test with 12 testers, then live on the Play Store |

Spending alerts come after the release, as the first update.

## Repository and architecture

The project is one public GitHub repo (`khata`) under GPLv3 containing the Android app split into modules, plus the parser website as a static folder. The app follows the MVVM pattern: each screen has a ViewModel that talks to repositories, which read and write the database.

```
khata/
├── app/                  # Entry point, navigation, app lock, Hilt setup
├── core/
│   ├── model/            # Plain Kotlin data classes (Transaction, Payee...)
│   ├── database/         # Room + SQLCipher, DAOs, migrations
│   ├── data/             # Repositories
│   ├── security/         # Keystore key, biometric/device-lock helpers
│   └── ui/               # Theme, shared Compose components, strings (en, hi)
├── feature/
│   ├── transactions/     # List, add/edit, review inbox
│   ├── payees/           # Payee memory screens
│   ├── categories/       # Categories and tags
│   ├── insights/         # Pie, heatmap, monthly chart, home summary
│   ├── csv/              # Import and export
│   └── settings/         # Lock, backup reminder, parsers, language
├── sms/
│   ├── parser/           # Pure Kotlin: bank parsers + rule engine (no Android deps)
│   └── ingest/           # SMS receiver, inbox scan, dedupe
├── parser-web/           # Static site for GitHub Pages (custom parser builder)
└── docs/                 # PRD, privacy policy, CSV format, contributing guide
```

**Conventions**

- Kotlin official style, checked by ktlint and detekt in CI.
- Amounts are always stored as `Long` paise; formatting in Indian style (₹1,00,000) happens only in the UI.
- All user-facing text lives in `strings.xml`, with `values/` for English and `values-hi/` for Hindi. A lint rule blocks hard-coded text.
- `sms/parser` has no Android dependencies, so the same rule engine can be reused by the parser website later (through Kotlin/JS) or tested quickly on a computer.

## Phase 0: Foundation

By the end of this phase the app opens behind the phone lock, stores data encrypted, and CI fails if anyone adds the internet permission.

- [ ] Create the GitHub repo with the GPLv3 license, README, `CONTRIBUTING.md`, issue templates and a code of conduct
- [ ] Set up the Gradle project: Kotlin, Compose, Material 3, Hilt, min SDK 26, target the latest SDK
- [ ] Split the project into the module structure above, with version catalog (`libs.versions.toml`)
- [ ] Add Room with SQLCipher; generate the database key on first launch and store it in the Android Keystore
- [ ] Write the first schema: Account, Category, Tag, Payee, Transaction, TransactionTag (amounts as paise), with Room migration tests
- [ ] Seed the default categories in English and Hindi
- [ ] App lock: BiometricPrompt using the phone's screen lock, lock again after a timeout, FLAG_SECURE
- [ ] Turn off Android backup (`allowBackup=false`, data extraction rules)
- [ ] GitHub Actions: build, unit tests, ktlint, detekt, Android lint
- [ ] CI check that fails if `android.permission.INTERNET` appears in the merged manifest
- [ ] App theme, icon placeholder, bottom navigation shell (Home, Transactions, Insights, Settings)

## Phase 1: Manual tracking

By the end of this phase you can track a full month of income and spending by hand, in English or Hindi.

- [ ] Accounts screen: add bank accounts, credit cards, debit cards and wallets (name, bank, last 4 digits)
- [ ] Add and edit transaction screen: amount, direction (expense, income, refund, transfer), date and time, account, payee, category, tags, note
- [ ] Transactions list: grouped by day, with search and filters for category, tag, account and date range
- [ ] Categories screen: add, rename, recolor, choose an icon, archive
- [ ] Tags: create them while adding a transaction, and rename or merge them later
- [ ] Payee memory: save the display name, default category and tags; fill them in automatically; allow an override for one transaction
- [ ] Payees screen: list, edit and merge payees
- [ ] Transfers are left out of spending and income totals
- [ ] Home summary: income and spending this month, spent today, top category
- [ ] Language switch in Settings (English and Hindi), using Android's per-app language setting
- [ ] Indian number formatting (₹1,00,000) everywhere amounts are shown
- [ ] Unit tests for repositories and totals; UI tests for adding and editing a transaction

## Phase 2: Charts and CSV

By the end of this phase you can import your existing spreadsheet and see all three charts.

- [x] A shared chart theme for light and dark mode, drawn with Compose Canvas (no chart library)
- [x] Category donut: top 5–6 categories plus "Other"; tap a slice to open its transactions; switch between week, month, year and a custom range
- [x] Calendar heatmap (custom Compose Canvas): the last 12 months, with 5 color levels based on the user's own spending; tap a day to open its list; scrolls sideways on small screens
- [x] Monthly comparison: bars for 6 or 12 months, optionally stacked by category, with the change against last month ("Food +18%")
- [x] Show income against spending on the monthly chart
- [x] Write down the CSV format in `docs/csv-format.md`
- [x] CSV export: all data or a date range, saved through the system file picker
- [x] CSV import: Khata's own format, plus a column-matching step for any other CSV; show a preview and skip duplicates
- [x] Backup reminder after 30 days without an export (interval can be changed), using WorkManager
- [ ] Tests: CSV export then import gives back the same data, and chart totals match the database totals

## Phase 3: SMS reading and custom parsers

By the end of this phase your bank SMS are recorded automatically, and anyone can add a bank using the parser website. This is the longest phase, because parsing needs real SMS samples to get right.

**Parsers for the built-in banks**

- [ ] Collect 5–10 real SMS samples for each bank (HDFC, ICICI, Federal, Axis, Kotak) with personal details blanked out, stored in `sms/parser/src/test/resources`
- [ ] Design the parser rule format: sender pattern, message pattern with named groups (amount, payee, account, reference, date), direction and a version number
- [ ] Rule engine that turns a rule and an SMS into a transaction, or nothing
- [ ] Write the built-in bank parsers as rules in the same format, so built-in and custom parsers work the same way
- [ ] Filter out OTP, promotional and balance-only messages
- [ ] Detect transfers: credit card bill payments (including CRED) and moving money between your own accounts
- [ ] Match duplicates by reference number, or by amount, time and account when there is no reference

**Reading SMS on the phone**

- [ ] Ask for SMS permissions only when the user turns SMS import on, with a clear explanation screen first
- [ ] Receiver for new SMS, plus a one-time scan of the inbox from a start date the user picks
- [ ] Review inbox: cards for new payees with a count badge; name, category and tags; skip
- [ ] Local daily summary notification (optional)
- [ ] Unparsed bank SMS appear in the review inbox with their raw text

**Custom parsers**

- [ ] Parser website in `parser-web/`: paste an SMS, highlight the fields, generate a rule code; runs fully in the browser; published on GitHub Pages
- [ ] In the app: Settings > Parsers > Add, paste the code, test it on a recent SMS, then save; list, disable and delete saved parsers
- [ ] Check every rule before saving: known version, valid pattern, required fields present, and a time limit so a bad pattern can't freeze the app
- [ ] Contributing guide for submitting a rule to the repo

**Target:** at least 95% of the sample SMS parse correctly in the test suite before the phase is done.

## Phase 4: Release

By the end of this phase Khata is live on the Play Store. Most of the wait is Google's required 14-day closed test, so start recruiting testers during Phase 3.

- [ ] Create the Play developer account and complete identity verification (start this in Phase 0, since verification can take days)
- [ ] Final app icon, feature graphic and screenshots in English and Hindi
- [ ] Privacy policy page on GitHub Pages: no data is collected, and nothing leaves the device
- [ ] Signed release build with R8 (code shrinking), app signing through Play, versioning scheme
- [ ] Closed test track with at least 12 testers for 14 days; fix what they report
- [ ] SMS permissions declaration form, with money management as the use case, and a 1–2 minute demo video
- [ ] Data safety form: no data collected, no data shared
- [ ] Store listing: "Khata: Private Expense Tracker", descriptions in English and Hindi
- [ ] GitHub release with the signed APK and changelog
- [ ] Apply for production access and roll out in stages (10%, 50%, 100%)

**After launch:** spending alerts (monthly limit per category, alerts at 80% and 100%) are the first update, followed by parsers for more banks based on GitHub requests.

## Testing, CI and quality gates

Every pull request must pass CI before it is merged. The privacy checks are treated like failing tests, not warnings.

| Check | Tool | When it runs |
| --- | --- | --- |
| Build, unit tests | Gradle, JUnit, Turbine for Flows | Every PR |
| Code style and static analysis | ktlint, detekt, Android lint | Every PR |
| No INTERNET permission | Script over the merged manifest | Every PR and release build |
| No new network or tracking libraries | Dependency allowlist check | Every PR |
| Database migrations | Room migration tests | Every PR |
| Parser accuracy | Test suite over the redacted SMS samples | Every PR touching `sms/` |
| UI tests | Compose UI tests on an emulator | Before merging to `main` |
| Missing Hindi strings | Lint check that every English string has a Hindi version | Every PR |

**Manual checks before each release:** test on at least one low-end phone (Android 8–10) and one recent phone, in light and dark mode, in both languages, and with the SMS permission granted and refused.

## Way of working

Work is tracked as GitHub issues and delivered as small pull requests, one feature or fix each.

- **Branches:** `main` is always releasable. Work happens on short-lived branches such as `feat/payee-memory` or `fix/hdfc-parser`.
- **Issues:** one GitHub milestone per phase, and one issue per checklist item in this plan. Labels: `feature`, `bug`, `parser`, `good first issue`, `hindi`.
- **Pull requests:** Claude opens a draft PR for each issue, keeps CI green, and asks you to review. You merge.
- **Releases:** a tag such as `v0.1.0` at the end of each phase creates a signed APK on GitHub Releases, so you can install every phase on your phone.
- **Commit messages:** Conventional Commits (`feat:`, `fix:`, `docs:`), which lets the changelog be generated automatically.

## Next steps

The first step is to create the repo, which only you can do. After that, Phase 0 work can start.

- [ ] You: create an empty public repo named `khata` on GitHub, and connect it to this project
- [ ] You: start the Google Play developer account registration, because identity verification can take several days
- [ ] Claude: open the first PR with the project skeleton, GPLv3 license, CI and the INTERNET-permission check
- [ ] Claude: create GitHub issues and milestones from this plan
- [ ] You: start saving bank SMS samples for Phase 3 (blank out names, account numbers and balances)
