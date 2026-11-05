# Verification record

## Complete balance demo and recorded settlements - November 4, 2026

Branch `feature/settlements-live-balances`, based on merged `main` at `41d912f`.
The separate settlement list uses faint dividers and creator-only edit icons.
Payment forms support create/edit/confirmed delete. Expense detail now adds the
missing editor while retaining the previous creator-only delete action. Balance
pages update on return and through foreground group-revision subscriptions.

| Check | Result |
| --- | --- |
| App and instrumentation APK builds, strict lint | PASS |
| Java tests | PASS: 57 tests, zero failures/errors |
| TypeScript contracts and financial validation | PASS: 8 tests |
| Firebase emulator suites | PASS: 30 tests |
| Full Android instrumentation, API 33 Pixel 4 AVD | PASS: 11 tests |

The full demo regression verifies $10 + $5 - $4 = $11 pairwise debt, the reciprocal
Bob view including an independent Carol debt, same-pair offset without transitive
simplification, separate $15 receivable/$4 payable totals, and another group's
$100 debt not affecting that group. It creates/edits/deletes settlements through
actual XML forms, hides another member's edit icon, preserves credits after a
settled expense is deleted, and creates/edits/deletes an expense through actual
screens. No manual Refresh button is used. Three SDK expense mutations are also
observed while the balance page remains open, proving foreground invalidation
rather than only reopen behavior.

State tests cover rotation/process recreation, immutable request replay after
ambiguous results, conflicts preserving dirty drafts, and unrelated read failures
not discarding unknown writes. Live-refresh tests cover coalescing in-flight
revisions, listener detachment and SDK session invalidation before queued worker
publication. New near-range settlement preview coverage permits an unbounded
replacement-debt intermediate while retaining bounded recorded/final ledgers.

Backend tests cover authoritative debts, actor-derived payer/creator, immutable
metadata, creator/member/outsider permissions, replacement debt with original
removed, overpayment, self-payment, common revision/concurrency guard, success and
error receipt replay/mismatch, ledger boundaries, group deletion and malformed
stored data. Both expense and settlement writers use the common transaction
runner and full financial snapshot.

Review also exposed preexisting percentage remainder and Java multiplication
issues. Both languages now allocate leftover cents to largest fractional remainder
with UID tie-break, using exact intermediates. Regression examples include one
cent at 10/90 percent, multiple leftovers, ties and maximum supported money.
Historical records with incorrect percentage allocations fail explicitly; a
reviewed reconciliation would be required before a deployment containing them.
Existing equal-split UI data remains compatible. No migration or deployment ran.

Visual QA inspected the settlement list/edit icon, payment form and separate
balance totals using synthetic Alice/Bob/Carol/David identities. Screenshots and
planning notes remain in ignored `tmp/settlements/`. Local tests use loopback
Firebase emulators and the installed API 33 AVD; cloud, physical-device and other
API-level runtime validation are not claimed. The app continues to target API 26
minimum, enforced by strict lint.


## Group balances (catalog feature 4) - November 3, 2026

Implemented on `feature/group-balances`, from `main` at `f48e3d0`.
Open a group and select **View balances**. The read-only screen shows amounts to
pay/receive and every other member, including zero balances. Refresh and resume
read the current coherent ledger. Offline settlements are included as source
records; settlement creation/edit/delete screens and APIs remain future work.

| Check | Result |
| --- | --- |
| Local app and instrumentation APK builds | PASS |
| Java unit/contract tests | PASS: 34 tests, no failures/errors |
| Strict Android lint | PASS |
| TypeScript contract checks | PASS: 3 tests |
| Firebase emulator regressions | PASS: 19 tests, including ledger authorization, integer money and malformed-data rejection |
| Full Android instrumentation | PASS: 6 tests on API 33, including existing identity/group/diagnostic flows |
| Larger text | PASS: 3 balance tests repeated with font scale 1.3 |

Calculator coverage includes canonical expense/payment examples, creator/payer
separation, reciprocal pair netting without transitive simplification, signed
credits after expense removal, all other members including zeros, deterministic
ordering, malformed ledgers and contract bounds. Intermediate totals above Java's
long range remain exact until final range checks. Service tests verify queued
worker/delivery behavior, read source/revision, transport errors, invalid scope,
range failure, cancellation, and SDK session changes preceding observer delivery.

Ledger tests use real Auth/Firestore/Functions emulators with synthetic integer
expense/share/settlement fixtures. Both collections are read in one transaction;
unsupported schemas or malformed amounts fail the whole ledger. Wire decoders
reject partial financial arrays and noncanonical money; the new settlement decoder
also rejects unknown fields. Existing canonical string expense storage is read
for compatibility; this feature does not migrate the earlier expense writer.

The Android balance flow exercises group-detail navigation, initial totals/member
names, expense edit plus manual refresh, rotation/reopening, maximum supported
amount display, deletion returning to settled state, missing scope and back
navigation. Screenshots were inspected with large text; planning notes and images
stay in ignored `tmp/balances/`.

The full suite reproduced an existing lifecycle race: a paused group screen could
redirect over the next registration while its old transition was still STARTED.
Protected screens now defer account redirects until RESUMED, suppress duplicate
redirects, and still clear private content immediately. The formerly failing
partial-registration recovery flow passed in the full instrumentation rerun.
Timeouts now capture a screenshot and the actual resumed activity/status.

Performance sample: Apple Silicon macOS host, Android 13/API 33 Pixel 4 AVD,
JBR 21.0.11, 500 synthetic expenses with three members, ten warmups and twenty
samples. Local calculator median was 1.34 ms and p95 3.07 ms, below the 250 ms
target. This measures calculation only, with no network or database I/O. It does
not establish 500-transaction server-refresh or cold-start recovery latency.
No cloud deployment, live credentials, physical-device test, or API 26/35 run was
performed for this feature. The emulator font setting was restored after visual QA.

Reproduce with the README prerequisites, an installed SDK and running AVD:

```bash
npm ci
./gradlew :app:assembleLocalDebug :app:assembleLocalDebugAndroidTest :app:testLocalDebugUnitTest :app:lintLocalDebug
npm run check:contracts
npm run check:emulators
npm run check:android
```


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
USC ID rejected its profile. The screen regression also rotates after editing both group fields and verifies
the unsaved values survive a fresh server read. Registered and unknown USC emails
produce the same reset confirmation through both the SDK and screen. Test
screenshots contain synthetic identities only.

Pre-PR verification fixed an emulator API-key format rejected by the Functions
SDK, resource/lint issues, Auth lookup dependency during receipt replay, navigation
that could discard pending mutation intent, and stale private screen text after
access loss. A cross-thread parent/child cancellation regression was added during
PR review, with child cancellation moved outside the parent lock. Codex review identified two P2 findings: dirty group fields lost during lifecycle
refresh and unknown-account reset responses revealing account existence. Saved
form drafts and reset-specific normalization address these findings, with Android
regression coverage. The final follow-up review and remote CI results belong to the PR;
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
