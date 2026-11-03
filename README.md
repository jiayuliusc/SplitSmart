# SplitSmart — Team 9

Native Android app for Team 9 (Salmon Noodles). User/group management and X01
password reset are implemented on contract baseline v1. Register, complete a
partially created profile, sign in/out, view/update your name, request a password
reset, and create/manage multiple groups. Group owners edit details, add registered
USC users, and delete groups through resumable backend cleanup.

Expense, split, balance, settlement, history and summary features remain interfaces
only. X06 email verification and all other optional features are outside this scope.

Local development uses Firebase emulators under **`demo-splitsmart`**. It needs
neither a Firebase account nor cloud credentials, `google-services.json`, billing,
or a service account. The first dependency/emulator download needs internet.

## Selected toolchain

| Component | Pinned / supported version |
| --- | --- |
| Android Studio | Meerkat 2024.3.1 Patch 2 baseline; newer stable IDEs that support AGP 8.9 are usable without upgrading project files |
| Gradle runtime JDK | JDK 21 (verified locally with 21.0.8); CI uses the latest available Temurin 21 |
| Java source/bytecode | Java 17 |
| Android Gradle Plugin / wrapper | 8.9.2 / 8.11.1 |
| SDK compile / target / minimum | API 35 / API 35 / API 26 |
| SDK Build Tools | 35.0.0 |
| Reference virtual device | API 35 Google APIs, arm64-v8a on Apple Silicon or x86_64 on Intel/CI |
| Material / AppCompat / Activity | 1.12.0 / 1.7.0 / 1.10.1 |
| ViewModel / LiveData | 2.8.7 |
| JUnit / Android test runner / Android test JUnit | 4.13.2 / 1.6.2 / 1.2.1 |
| Firebase Android BoM | 34.4.0 (Auth, Firestore, Functions) |
| Node.js / npm | 22.12.0 / 10.9.0 |
| Firebase CLI | 14.22.0, installed locally by npm |
| TypeScript / Functions SDK / Admin SDK | 5.9.3 / 6.4.0 / 13.5.0 |

All direct versions are pinned; `package-lock.json` fixes npm transitive versions.
Commit the complete Gradle wrapper, including its JAR. The distribution checksum
is checked by the wrapper. Do not accept an IDE upgrade suggestion in a feature PR.
AGP 8.9's compatibility table specifies Gradle 8.11.1 and SDK 35
([Android documentation](https://developer.android.com/build/releases/agp-8-9-0-release-notes)).
JDK 21 runs both Gradle and the emulator suite; Java source stays at 17.

## Fresh clone on macOS or Linux

1. Clone the shared repository and enter its root:

   ```bash
   git clone git@github.com:jiayuliusc/SplitSmart.git SplitSmart
   cd SplitSmart
   ```

2. Install JDK 21 and set `JAVA_HOME`. For example, on macOS:

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   export PATH="$JAVA_HOME/bin:$PATH"
   java -version
   ```

   On Linux, set `JAVA_HOME` to your installed JDK 21 directory. Do not point it
   at a JRE. In Android Studio, set **Settings → Build, Execution, Deployment →
   Build Tools → Gradle → Gradle JDK** to the same JDK 21.

3. In Android Studio's SDK Manager, install Android SDK Platform 35, Build Tools
   35.0.0, Platform Tools, Android Emulator, and Android SDK Command-line Tools.
   Install an API 35 Google APIs system image for your CPU architecture. Android
   Studio **Open** must point at this repository root (`settings.gradle`). Allow
   Gradle sync and select **localDebug** in Build Variants.

   Studio normally creates ignored `local.properties` automatically. Otherwise
   create it with `sdk.dir=/absolute/path/to/Android/sdk` (no surrounding quotes).
   For CLI tools on macOS:

   ```bash
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
   sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools" "emulator"
   sdkmanager --licenses
   ```

   Linux's usual SDK location is `$HOME/Android/Sdk`. SDK licenses must be accepted
   by each developer. The repository does not commit your SDK location or licenses.

4. Install Node.js 22.12.0. With nvm:

   ```bash
   nvm install
   nvm use
   node --version
   npm --version
   npm ci
   npm run functions:build
   ```

   Run `npm ci` **at the root**; it installs the Functions workspace too. Do not
   install a global Firebase CLI. Use the pinned npm scripts.

## Start the local environment

Terminal 1 (repository root, Node 22 and JDK 21 selected):

```bash
npm run emulators
```

Wait for the emulator readiness table. Terminal 2:

```bash
npm run diagnostics:seed
npm run diagnostics:verify
```

The seed creates only `_diagnostics/connectivity`, a synthetic fixture. It does
not create a user, group, expense, or settlement. The host check confirms Auth
availability, Functions registration, the exact Firestore read, and denials for
listing, unrelated reads, and client writes. It is **not** evidence that Android
can reach the database; run the separate Android check below.

| Service | Computer address | Android Virtual Device address |
| --- | --- | --- |
| Emulator UI | `http://127.0.0.1:4000` | Not used by the app |
| Auth | `127.0.0.1:9099` | `10.0.2.2:9099` |
| Firestore | `127.0.0.1:8080` | `10.0.2.2:8080` |
| Functions (`us-central1`) | `127.0.0.1:5001` | `10.0.2.2:5001` |
| Hub / logging | `127.0.0.1:4400` / `127.0.0.1:4500` | Not used by the app |
| Firestore WebSocket | `127.0.0.1:9150` | Not used by the app |

`10.0.2.2` is the Android Virtual Device's alias for the host loopback address
([Firebase Android emulator setup](https://firebase.google.com/docs/emulator-suite/connect_firestore)).
The supplied workflow targets an Android Virtual Device, not a physical phone.
The local flavor permits cleartext **only** to `10.0.2.2`; cloud forbids cleartext.
All emulators bind to host loopback.

Seven identity/group callables and cleanupDeletedGroup should be discovered.
A missing handler or Functions build/load error is a failure. No login is needed for this
demo project. Ctrl+C stops the suite. Local data is intentionally ephemeral:
restart and reseed to reset. No user data is imported or exported by default.

## Build, launch, and verify Android connectivity

Create/start an API 35 device in Android Studio **Device Manager**, then:

```bash
adb devices
./gradlew :app:assembleLocalDebug :app:lintLocalDebug
./gradlew :app:installLocalDebug
adb shell am start -n edu.usc.splitsmart.local/edu.usc.splitsmart.presentation.identity.LoginScreen
```

Alternatively select the **app** run configuration and **localDebug**, choose the
running device, and click Run. Expected screen: **SplitSmart / Welcome back**, or your groups if a complete
account is already signed in. Registration enters the app only after profile creation.

With Firebase emulators running and the fixture seeded:

```bash
./gradlew :app:connectedLocalDebugAndroidTest
```

This installs a local-only instrumentation test APK. It performs an unauthenticated
Android Firestore SDK `Source.SERVER` read of `_diagnostics/connectivity`, checks
the expected message, project ID and schema version, and rejects a cached result.
It uses the same Application and configured SDK instances as the product app.
The same suite also exercises the real Auth/group adapters and XML screen flows,
including partial registration recovery and reset confirmation. Test sources are in `app/src/androidTestLocal`; it is never packaged into
the application or cloud variant. It times out rather than reporting offline
cache data as success. Test results are under
`app/build/reports/androidTests/connected/debug/`.

For one command that starts/stops Firebase emulators, seeds, checks rules, and
runs the Android test (start the Android Virtual Device first; stop any existing
Firebase suite to free its ports):

```bash
npm run check:android
```

For host-only feature, authorization and diagnostic tests without an Android device:

```bash
npm run check:emulators
```

The local APK is `app/build/outputs/apk/local/debug/app-local-debug.apk`.
Cloud variants are disabled unless explicitly opted into. Local release builds
are disabled, so emulator networking cannot become a release artifact accidentally.

## Native Windows workflow

Use Android Studio, SDK, emulator, JDK 21 and Node 22.12.0 **all on Windows**.
WSL is not required and mixing a WSL Firebase process with a Windows Android
emulator is not supported by this setup's loopback configuration.

In PowerShell, adjust the JDK path to your installation:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
node --version
java -version
npm.cmd ci
npm.cmd run emulators
```

In a second PowerShell window, use the same environment setup:

```powershell
npm.cmd run diagnostics:seed
npm.cmd run diagnostics:verify
.\gradlew.bat :app:assembleLocalDebug :app:lintLocalDebug :app:installLocalDebug
adb shell am start -n edu.usc.splitsmart.local/edu.usc.splitsmart.presentation.identity.LoginScreen
.\gradlew.bat :app:connectedLocalDebugAndroidTest
```

`npm.cmd run check:android` also selects `gradlew.bat` automatically. Let Studio
write `local.properties` to handle Windows path escaping. Use `npm.cmd` if
PowerShell execution policy blocks `npm.ps1`; no policy change is required.

## Optional shared cloud configuration

Local work is fully independent of this section. No cloud resources were created,
billing enabled, or code deployed by the foundation task.

The owner must choose a shared Firebase project and grant team access. Register an
Android app with package **`edu.usc.splitsmart`**, select the intended Firestore
location and Functions region, and keep database access denied until authorized
feature rules are reviewed. The local application ID is different
(`edu.usc.splitsmart.local`), so both apps can coexist.

Copy `config/cloud.properties.example` to ignored `cloud.properties` and fill it
with `project_id`, `mobilesdk_app_id`, and `current_key` from the owner's Firebase
Android configuration, plus the agreed Functions region. The properties names in
the template explain the mapping. Client configuration values are embedded in
the APK; they are not server credentials and do not grant database authorization.
Never put a service account key in an Android project.

```bash
cp config/cloud.properties.example cloud.properties
# Edit cloud.properties locally, then explicitly enable cloud variants:
./gradlew -PenableCloud=true :app:assembleCloudDebug
```

PowerShell equivalent: `Copy-Item config/cloud.properties.example cloud.properties`
then `.\gradlew.bat -PenableCloud=true :app:assembleCloudDebug`.
For Studio, add `enableCloud=true` to your **user** Gradle properties
(`~/.gradle/gradle.properties`, or `%USERPROFILE%\.gradle\gradle.properties`) and
sync; remove it to return to local-only variants. Do not commit that opt-in.
Cloud builds fail early if the required properties are missing.

Manual `FirebaseOptions` initialization is used in both environments; the Google
Services Gradle plugin and a real `google-services.json` are not needed. Auth and
Functions SDKs power identity/group flows. Enable the email/password Auth provider
in a real cloud project and configure its reset-email template before cloud use.

`firebase.json` references the reviewed self-profile/member-group read rules; all
client product writes remain denied. `firebase.local.json` alone adds the diagnostic exception. Never deploy the local configuration.
Before a future deployment, review rules and callable authorization together and
use the real project ID explicitly rather than changing the demo default alias.
Functions deployment requires a suitable billing plan; that is a separate future
owner decision ([Firebase Functions setup](https://firebase.google.com/docs/functions/get-started)).
Cloud credentials, connectivity, authorization, signing and deployment are not
verified by the local smoke test. No deployment commands are run by CI.

## Team work and one-time owner setup

The shared repository is [jiayuliusc/SplitSmart](https://github.com/jiayuliusc/SplitSmart),
with remote `git@github.com:jiayuliusc/SplitSmart.git`. The local `main` branch already
tracks `origin/main`; do not create a replacement repository or add another origin.
The v1 contract baseline is merged. Update `main` and create a dedicated feature
branch before starting work; see CONTRIBUTING.md.

The owner should confirm team access and branch protection requiring PR review,
`Android build and lint`, and `Firebase emulators`. CI needs no cloud secrets.
Jiayu Li (`jiayuliusc`) coordinates changes to the v1 shared contracts.

Start with [CONTRIBUTING.md](CONTRIBUTING.md) and the finalized v1 coordination contracts:

| Workstream | Start in presentation package | Planned scope |
| --- | --- | --- |
| A | `identity`, `groups` | F01–F06: identity, profiles, groups |
| B | `expenses` | F07–F12: expenses and split previews |
| C | `balances` | F13–F14: pairwise balances and refresh |
| D | `settlements` | F15–F16: offline settlements |
| E | `history` | F17–F21: history and monthly summaries |

All packages are under `app/src/main/java/edu/usc/splitsmart/`.
See [architecture](docs/ARCHITECTURE.md), [v1 schema](docs/SCHEMA.md),
[v1 contracts](docs/CONTRACTS.md), [source decisions](docs/DESIGN_DECISIONS.md),
and [verification results](docs/VERIFICATION.md). Identity/group implementations now use the shared domain types and callable
payloads. Financial interfaces remain unimplemented. See
[the feature map](docs/USER_GROUP_IMPLEMENTATION.md) for screens, layers and tests.

Check shared JSON compatibility in both languages:

```bash
./gradlew :app:testLocalDebugUnitTest
npm run check:contracts
```

The common corpus is `contracts/v1/fixtures.json`. Financial expectations are
recorded separately in `contracts/v1/financial-expectations.json`; they are not
implemented calculator tests. CI runs the Java and TypeScript compatibility checks.

## Troubleshooting

| Symptom | Check / fix |
| --- | --- |
| `JAVA_HOME` invalid, unsupported class version, emulator Java error | Run `java -version` and `./gradlew --version`; use JDK 21 in both terminal and Studio. Stop old daemons with `./gradlew --stop` after changing JDK. |
| SDK location or API missing | Open SDK Manager, install Platform 35 and Build Tools 35.0.0; set ignored `local.properties` or `ANDROID_HOME`; accept licenses. |
| Wrapper download fails | Check internet/proxy access to Gradle, Google Maven and Maven Central. Keep checksum validation enabled. Retry after fixing the connection. |
| `gradlew` permission denied | Run `chmod +x gradlew`; Windows uses `gradlew.bat`. |
| npm engine/build errors | Select Node 22.12.0, then `npm ci` at the root; do not use the machine's unrelated global Node/Firebase CLI. |
| Ports already in use | Stop the earlier Firebase suite. Check 4000, 4400, 4500, 5001, 8080, 9099, 9150 with `lsof -nP -iTCP -sTCP:LISTEN` or Windows `Get-NetTCPConnection -State Listen`. Do not kill unrelated processes. |
| Project mismatch warning | Keep `.firebaserc`, npm scripts and local `BuildConfig` at `demo-splitsmart`; use `npm run emulators`, not a command pointing at a real project. |
| Permission denied reading diagnostic | Ensure `firebase.local.json` is selected. Only the exact `_diagnostics/connectivity` get is permitted; signed-out product reads, private collections and direct product writes are denied. |
| Diagnostic missing | Rerun `npm run diagnostics:seed` after every emulator restart. |
| Android connection timeout / cleartext error | Use `localDebug` on an Android Virtual Device on the same host. `localhost` inside Android is the device; the app must use `10.0.2.2`. Verify Firebase ports on the host and avoid WSL/native mixing. |
| No connected devices | Start an AVD, wait for the home screen, and inspect `adb devices`. If several devices are attached, disconnect extras or set `ANDROID_SERIAL` to the intended AVD. |
| Test fails because a user is signed in | The diagnostic expects a signed-out start. Sign out in the local app, or clear only this local app's data with `adb shell pm clear edu.usc.splitsmart.local`, then rerun. |
| Cloud tasks unavailable | Intentional: configure `cloud.properties` and opt in with `-PenableCloud=true`; do not modify the local flavor. |
| Missing groups or identity endpoints | Start the emulators, finish profile registration, and verify seven identity/group callables are loaded. Financial endpoints remain unimplemented. |

The diagnostic checks one fresh read. The feature suites additionally exercise
authentication, privacy, contention, receipts and cleanup. See the verification
record for exact coverage and remaining platform limitations.
