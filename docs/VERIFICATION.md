# Verification record

## User/group management and X01 — November 2, 2026

Scope is catalog section 1 and password reset X01. X06 and all financial, history,
summary and other optional features remain unimplemented. Started from merged
`main` at `61ad650` on `feature/user-groups-password-reset`.

| Check | Local result |
| --- | --- |
| Android app + test APKs | PASS: `:app:assembleLocalDebug :app:assembleLocalDebugAndroidTest` |
| Java unit/compatibility tests | PASS: `:app:testLocalDebugUnitTest`, 13 tests, no failures/errors/skips |
| Strict Android lint | PASS: `:app:lintLocalDebug`, warnings treated as errors |
| TypeScript contract build/checks | PASS: `npm run check:contracts`, 3 compatibility checks |
| Firebase emulator feature tests | PASS: `npm run check:emulators`, host/rules diagnostic plus 11 tests including nested cases |
| Android SDK and screen integration | PASS: `npm run check:android`, 3 API 35 instrumentation tests |

The Java tests cover exact USC domain and ASCII ID validation, queued callbacks,
Auth success/profile failure with profile-only retry, cancellation, session changes,
login/logout, reset-email confirmation/transport errors, and cancellation racing
a child callback without lock-order deadlock. Shared fixture tests
still cover all v1 wire definitions and immutable collection ownership.

The backend tests exercise real Auth/Firestore/Functions emulators: duplicate email,
competing USC ID claims, partial registration, private fields and denied direct
writes, multiple groups, owner/member/outsider permissions, registered email lookup,
stale concurrent revisions, success/error receipt replay and payload mismatch,
member receipt replay while Auth lookup is unavailable, DELETING exclusion, cleanup
of 250 synthetic child documents across bounded batches, duplicate cleanup delivery,
and Firebase reset action application followed by old/new password login checks.
Synthetic expense/settlement fixtures verify deletion only; no financial algorithms
or financial APIs are implemented.

The Android integration tests use the real composition root and SDK adapters. They
cover registration, profile/name changes, group creation/replay, member addition,
membership projection, stale revisions, reset requests, deletion and signed-out
access. The XML screen test follows registration → groups → creation/details →
profile → logout → reset → login, then recovers a second account after a duplicate
USC ID rejected its profile. Test screenshots contain synthetic identities only.

Pre-PR verification fixed an emulator API-key format rejected by the Functions
SDK, resource/lint issues, Auth lookup dependency during receipt replay, navigation
that could discard pending mutation intent, and stale private screen text after
access loss. A cross-thread parent/child cancellation regression was added during
PR review, with child cancellation moved outside the parent lock. Codex PR review findings and remote CI results belong to the PR;
local results alone do not establish a GitHub check's status.

## Environment and reproduction

- macOS Apple Silicon; JDK 21.0.8; Java source/target 17; Gradle 8.11.1; AGP 8.9.2.
- Android SDK/build tools 35; API 35 Android 15 arm64 AVD.
- Node 22.12.0, npm 10.9.0, Firebase CLI 14.22.0; demo project `demo-splitsmart`.
- Auth, Firestore and Functions emulators use loopback; the AVD uses `10.0.2.2`.

Use the README prerequisites, start an API 35 AVD and stop other Firebase suites:

```bash
npm ci
./gradlew :app:assembleLocalDebug :app:assembleLocalDebugAndroidTest :app:testLocalDebugUnitTest :app:lintLocalDebug
npm run check:contracts
npm run check:emulators
npm run check:android
```

The combined emulator checks start/stop their own suites. With a persistent suite
instead, seed the diagnostic fixture and run `connectedLocalDebugAndroidTest`.
Generated reports live under `app/build/reports`, JUnit XML under
`app/build/test-results` and `app/build/outputs/androidTest-results`.

## Limits

- No cloud deployment, live email delivery, billing, signing or real-project
  connectivity was exercised. Cloud setup must enable the email/password provider,
  deploy the reviewed rules/indexes/functions and configure the matching region.
- Deletion uses a durable job and retry-enabled event trigger. Production operators
  must monitor stalled jobs and re-drive cleanup if the platform retry window expires;
  no public administrative cleanup endpoint is exposed.
- Only API 35 on the reference AVD was exercised. API 26–34, physical devices,
  other sizes, and native Windows execution remain unverified locally.
- No 500-transaction benchmark, financial algorithm, cross-group summary or X06
  behavior is claimed by these tests.

## Earlier baseline evidence

The November 1 infrastructure checks verified wrapper checksums, local/cloud
compilation (cloud used synthetic properties only), emulator routing, and the
signed-out diagnostic read. The November 2 contract baseline added 5 Java and 3
TypeScript shared-fixture checks. Those commits intentionally contained no product
implementation; the feature results above supersede that limitation for this scope.
