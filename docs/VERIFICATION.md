# Verification record

## Contract baseline v1 — November 2, 2026

Verified on macOS/Apple Silicon with the environment listed below. These checks
exercise shared definitions and existing infrastructure, not product behavior.

| Check | Result |
| --- | --- |
| Android app and diagnostic APK builds | PASS: `:app:assembleLocalDebug :app:assembleLocalDebugAndroidTest` |
| Java compatibility | PASS: `:app:testLocalDebugUnitTest`, 5 tests, zero failures/errors/skips |
| Android lint | PASS: `:app:lintLocalDebug`, no issues |
| TypeScript build and compatibility | PASS: `npm run check:contracts`, build plus 3 checks |
| Firebase host/rules diagnostic | PASS: `npm run check:emulators`, all three emulators, fresh fixture read, denied list/unrelated reads/client write |
| Android SDK connectivity | PASS: `npm run check:android`, one API 35 instrumentation test, zero failures/errors/skips, signed-out server read |
| Shared boundary review | No SDK types in domain/repository/service contracts; no product handlers, implementations or financial algorithms; product rules unchanged and denied |
| Documentation and patch hygiene | Relative source links resolve; `git diff --check` passes |

Both language checks consume `contracts/v1/fixtures.json`. Coverage includes every
callable request/response, enum and ownership map; primitive string IDs; optional
omission versus explicit null; exact bounded decimal cents/revisions; timestamp
seconds/nanoseconds and microsecond precision; and incompatible/unknown fields.
Java also checks defensive collection ownership. Test-only encoding assertions
are not production serializers or validators.

The five financial examples in `contracts/v1/financial-expectations.json` are
acceptance expectations, **not passing financial algorithm tests**. The transaction
design was checked against installed Admin/Firestore SDK declarations and official
documentation linked in CONTRACTS.md. No financial contention, cleanup worker,
500-transaction performance benchmark, cloud deployment or feature flow was run.
The shared backend runner and runtime codecs remain feature implementation work.

CI now runs both compatibility suites alongside the existing build/emulator jobs.
Local results above do not establish the result of a GitHub Actions run; consult
the baseline PR for remote checks. The dedicated baseline branch is submitted for
review and must not be assumed merged. Teammates should branch from the exact
commit recorded in its handoff/PR.

## Original infrastructure verification — November 1, 2026

Verified locally on **November 1, 2026 (America/Los_Angeles)**. This records
infrastructure evidence only; no business requirement is marked implemented.

## Environment

- macOS on Apple Silicon (arm64).
- Oracle JDK 21.0.8, Java source/target 17; Gradle 8.11.1; AGP 8.9.2.
- Android SDK Platform 35 and Build Tools 35.0.0.
- API 35 / Android 15 `Medium_Phone_API_35` AVD, arm64 Google APIs Play Store image,
  Android Emulator 37.1.11.0; launched read-only without saving snapshots.
- Node 22.12.0, npm 10.9.0, Firebase CLI 14.22.0.
- Auth, Firestore (emulator 1.19.8) and Functions on loopback,
  project `demo-splitsmart`; no cloud credentials or Google Services JSON.

## Results

| Check | Result |
| --- | --- |
| Official Gradle wrapper JAR SHA-256 | PASS: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046` |
| Gradle distribution checksum | PASS: wrapper downloaded and verified the pinned 8.11.1 distribution |
| npm lockfile install | PASS: `npm ci --offline --cache /tmp/splitsmart-npm-cache --no-fund --no-audit` after initial download |
| Functions TypeScript build | PASS: `npm run functions:build`; empty handler exports intentional |
| Local APK and diagnostic APK | PASS: `:app:assembleLocalDebug :app:assembleLocalDebugAndroidTest` |
| Local Android lint | PASS: `:app:lintLocalDebug`; report says `No issues found.` |
| Emulator startup and host diagnostic | PASS: `npm run check:emulators`; Auth/Firestore/Functions registered, synthetic record seeded/read |
| Emulator diagnostic rules | PASS: unauthenticated exact get succeeds; listing, unrelated diagnostic/product reads and client write return HTTP 403 |
| Android SDK connectivity | PASS: `npm run check:android`; one instrumentation test, zero failures/errors/skips; signed-out `Source.SERVER` read checks fixture and rejects cache |
| Cloud configuration/build/lint | PASS for compilation only: `-PenableCloud=true :app:assembleCloudDebug :app:lintCloudDebug`, using temporary synthetic properties, then deleting them |
| Configuration exclusion | PASS: local properties, cloud properties, Google Services JSON, service-account files, generated code and emulator data are ignored |

The Android test logs:

```text
PASS: Android read _diagnostics/connectivity from demo-splitsmart (server)
```

The final APK is also installed and launched on the reference AVD; the static
screen identifies SplitSmart as the Team 9 development foundation. It contains
no product workflow. The diagnostic runs in a separate local instrumentation APK.

Generated, ignored reports:

- `app/build/reports/lint-results-localDebug.html`
- `app/build/reports/lint-results-cloudDebug.html`
- `app/build/reports/androidTests/connected/debug/flavors/local/index.html`
- JUnit XML and diagnostic logcat under
  `app/build/outputs/androidTest-results/connected/debug/flavors/local/`

Initial verification caught a Gradle variable/flavor name collision and missing
Android 12 data-extraction rules; both were corrected before the passing build
and lint run. npm/Firebase emit upstream deprecation and available-version notices,
and the Android build reports an unstripped dependency native library. These did
not fail the build, lint, emulator startup, or diagnostic; dependency upgrades
remain coordinated changes, not automatic modifications.

## Reproduce

Follow the README prerequisite setup, use Node 22.12.0/JDK 21, start an API 35 AVD,
and run from the repository root with no cloud configuration:

```bash
npm ci
./gradlew :app:assembleLocalDebug :app:assembleLocalDebugAndroidTest :app:lintLocalDebug
npm run check:android
./gradlew :app:installLocalDebug
adb shell am start -n edu.usc.splitsmart.local/edu.usc.splitsmart.presentation.foundation.FoundationScreen
```

The combined check starts/stops its own Firebase suite; stop other suites first.
For a continuously running suite, use the README's `npm run emulators`, seed,
host verify and `connectedLocalDebugAndroidTest` commands in separate terminals.
The test must fail if the server is unavailable or the fixture is absent; cached
data does not satisfy it.

## Not verified / remaining owner actions

- No real cloud project connection, cloud rules deployment, Functions deployment,
  billing, production signing or Firebase authentication workflow was tested.
  Cloud build success with synthetic values is not cloud connectivity evidence.
- GitHub Actions commands passed locally; remote run status is available on the
  repository/PR. The shared remote is now `git@github.com:jiayuliusc/SplitSmart.git`.
  Team invitations and branch protection/check requirements remain owner-managed.
- Native Windows instructions and the `.bat` wrapper are included; Windows and
  Linux execution were not tested on this macOS host. Run the README workflow on
  those platforms before claiming support has been exercised there.
- API 26–34 devices, physical devices, and other screen sizes were not exercised.
- No product feature, authorization flow, financial/concurrency algorithm,
  durability target or performance threshold has been implemented or tested.
