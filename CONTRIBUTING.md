# Contributing to Khata

Thanks for taking a look. This project is early — the fastest way to help is picking up an open issue from the first milestones in `docs/DEVELOPMENT_PLAN.md`.

By contributing, you agree your contributions are licensed under the project's [GNU GPL v3 license](LICENSE).

## Ground rules

- **Scope is defined by the [PRD](docs/PRD.md).** Anything outside it is out of scope by default. If you think something should be added, open an issue proposing a PRD change first; don't send a PR that quietly expands scope.
- **Privacy is a feature.** Khata is free, has no ads, and never collects, stores remotely, or shares user data. PRs that add analytics, tracking, ads, accounts, or network calls that send user data will not be accepted. The PRD's privacy principles are hard rules: the app has **no `INTERNET` permission** (CI fails if any dependency adds it), dependencies are limited to AndroidX, Kotlin libraries and SQLCipher, and Android backup stays off. See [`docs/PRIVACY.md`](docs/PRIVACY.md).

## Before you start

1. Check open issues for what's already claimed.
2. For anything nontrivial, open an issue first (or comment on an existing one) describing your approach before writing code.
3. New to the codebase? Start with `docs/DEVELOPMENT_PLAN.md`.

## Development phases

Each phase is a GitHub milestone, with one issue per checklist item in the development plan.

| Phase | Focus |
|---|---|
| 0 Foundation | Module split, encrypted Room database, app lock, CI privacy checks, navigation shell |
| 1 Manual tracking | Accounts, transactions, categories, tags, payee memory, English + Hindi |
| 2 Charts and CSV | Donut, calendar heatmap, monthly comparison, CSV export/import, backup reminder |
| 3 SMS and custom parsers | Bank parsers, inbox scan, review inbox, transfer detection, parser website |
| 4 Release | Closed test, Play forms, store listing, staged rollout |

**Adding a bank parser is a great first contribution:** a parser rule plus 5–10 redacted sample SMS as tests (Phase 3 onward).

## Branching & commits

- `main` is **production-ready only** — there's no develop branch. Work happens on feature branches (or forks) and reaches `main` through reviewed PRs.
- Branch from `main` with a short descriptive name (e.g. `feature/offline-sync`, `fix/crash-on-rotate`).
- Keep commits scoped and use a short imperative subject line, with body context for anything non-obvious.
- Reference the issue number in the PR description (`Closes #12`).

## Code style

- Kotlin + Jetpack Compose, following standard [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Run `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest` and `python3 .github/scripts/check-hardcoded-text.py` before opening a PR — CI runs the same checks and must be green to merge. `./gradlew ktlintFormat` fixes most style issues. detekt's settings are in `config/detekt/detekt.yml`; prefer fixing a finding, and use a narrow `@Suppress("Rule")` with a comment when the code is deliberate.
- Prefer small, single-purpose changes that follow the existing module layout rather than cross-cutting changes.
- **Modules:** new code goes in the module whose README owns it (see the layout in `README.md`). A new module uses the convention plugins from `build-logic/` (`khata.android.library`, `khata.android.compose`, `khata.android.hilt` or `khata.jvm.library`) instead of repeating Android/Kotlin setup.
- **Versions:** add or change library and plugin versions only in `gradle/libs.versions.toml`.
- **New libraries:** everything that reaches the app must be in `config/dependency-allowlist.txt` (PRD privacy principle 2), or CI fails. To add one, add a line with the reason, and say in the PR what the library does and that it makes no network connections. Analytics, crash reporting, ads and anything that talks to a server won't be accepted.
- **No hard-coded UI text:** a screen's text comes from `stringResource()`, never a literal like `Text("Hello")`. Previews go in files named `*Preview.kt`; a rare deliberate literal (e.g. a brand name) needs `// allow-hardcoded-text: <reason>` on its line.
- **Dependency injection:** ViewModels are `@HiltViewModel` and screens get them with `hiltViewModel()` in a small `…Route` composable; the screen itself stays a plain, previewable composable that takes state as parameters.
- **Screenshots and screen recording:** "Block screenshots" is on by default in every build (`FLAG_SECURE`). Turn it off in Settings on your test device for store screenshots and bug-report recordings.
- **Strings:** a screen's strings live in its own module's `res/values/` and `res/values-hi/`; shared ones (navigation labels) live in `:core:ui`.

## Testing expectations

- Business logic and state machines need unit tests, not just manual verification.
- Note in your PR description which devices and Android versions you tested on.
- **Money is `Long` paise**, never `Double`. Totals need tests that cover transfers (excluded from spending) and refunds.
- **Database changes need a Room migration and a migration test.** A lost migration means lost user data — there is no cloud copy. Commit the exported schema JSON in `core/database/schemas/`.
- **Instrumented tests** (Keystore, SQLCipher, Room on a real device or emulator) live in each module's `src/androidTest/`. CI doesn't run them yet, so run them before opening a PR that touches `:core:security` or `:core:database`: `./gradlew connectedDebugAndroidTest` with an emulator running.
- **Parser changes need sample SMS tests** with every personal detail (names, account numbers, balances, reference numbers) blanked out. Never commit a real, unredacted SMS.
- **Every new English string needs a Hindi version** in `values-hi/strings.xml`.
- Before a release, test on a low-end phone (Android 8–10) and a recent one, in light and dark mode, both languages, and with SMS permission granted and refused.

## Pull requests

- Keep PRs scoped to one feature/module where possible.
- Describe what you tested and on what devices.
- Update `docs/` if your change affects architecture, scope, or risks.

## Testing your PR on real phones via Google Play

You don't need to wait for a merge to put your change in testers' hands. A maintainer can publish your PR to the separate **Khata QA** app on Google Play — as an install link, or to your own closed testing track. See [`docs/TESTING_ON_PLAY.md`](docs/TESTING_ON_PLAY.md).

## Continuous integration

Every PR and push to `main` runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml): Gradle wrapper validation, ktlint, detekt, the hard-coded text check, Android lint, unit tests, debug and release builds, the **no-INTERNET-permission check** over every merged manifest, and the **dependency allowlist** check. The debug APK is attached to the run as an artifact for quick on-device testing.

## Releasing (maintainers)

Releases are cut by pushing a `vX.Y.Z` tag on `main`, which uploads a signed build to Google Play internal testing; the same build is then promoted through closed/open testing to a staged production rollout via the **Promote** workflow. See [`docs/RELEASING.md`](docs/RELEASING.md).

## Reporting bugs / requesting features

Use the issue templates, and include your device model and Android version.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Please read it before participating.
