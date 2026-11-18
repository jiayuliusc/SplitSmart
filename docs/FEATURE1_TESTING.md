# Feature 1 test record

**Project:** SplitSmart, Team 9
**Contribution author:** John (workspace account; full name and email were not supplied)
**Branch:** `feature/user-group-management`

This record documents the five Espresso black-box tests and five JUnit white-box tests added for this contribution. Existing Feature 1 functionality was already present when the branch was created; this contribution adds focused coverage and enables JaCoCo unit-test reports.

## Running the tests

Use JDK 21, Android SDK 35, Node 22, npm dependencies (`npm ci`), and an API 35 AVD. From the repository root:

```bash
./gradlew :app:testLocalDebugUnitTest --tests edu.usc.splitsmart.registration.IdentityFeatureWhiteBoxTest
./gradlew :app:testLocalDebugUnitTest
./gradlew :app:createLocalDebugUnitTestCoverageReport
npm run check:android
npm run check:emulators
npm run check:contracts
```

To run only the five contribution Espresso tests, first boot an API 35 AVD, then run:

```bash
firebase emulators:exec --config firebase.local.json --project demo-splitsmart --only auth,firestore,functions "npm run diagnostics:seed && npm run diagnostics:verify && ./gradlew :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=edu.usc.splitsmart.registration.Feature1EspressoTest"
```

The `npm run check:android` command starts the local Auth, Firestore and Functions emulators, seeds only the synthetic connectivity diagnostic, checks Firestore denials, and runs the connected Android instrumentation suite. It requires an already booted AVD. The direct Gradle equivalent for a manually managed emulator stack is `./gradlew :app:connectedLocalDebugAndroidTest`.

**Executed results:** all five contribution Espresso tests passed on `Pixel_9_API_35` (Android 15); all five contribution JUnit tests passed; and the full local JUnit suite passed. `npm run check:emulators` passed 35 tests and `npm run check:contracts` passed 9 tests, both with no failures. `npm run check:android` passed all 17 connected instrumentation tests. `./gradlew :app:lintLocalDebug` and `./gradlew :app:createLocalDebugUnitTestCoverageReport` passed. The final connected-suite report is at `app/build/reports/androidTests/connected/debug/flavors/local/index.html`.

## Espresso black-box tests

File: `app/src/androidTestLocal/java/edu/usc/splitsmart/registration/Feature1EspressoTest.java`. Run with `npm run check:android` or `./gradlew :app:connectedLocalDebugAndroidTest` with the required emulators and API 35 AVD.

| Test | Description | Rationale |
| --- | --- | --- |
| `registrationRejectsNonUscEmailAndMalformedUscId` | Enters an outside-domain email, then a valid USC email with a 9-digit ID; checks both validation responses on the registration screen. | Chooses values just outside the exact domain and fixed-length ID rules to catch boundary validation errors at the actual UI/backend boundary. |
| `registrationRejectsDuplicateEmailAndDuplicateUscId` | Registers an account, attempts the same email again, then uses a new email with the claimed USC ID and checks the profile-recovery response. | Separates Firebase Auth email uniqueness from the server-owned transactional USC ID claim and verifies that duplicate identity cannot complete registration. |
| `accountLifecycleUpdatesNameAndKeepsIdentifiersImmutable` | Registers, views email and USC ID, changes the name, logs out, signs back in and verifies the changed name and original identifiers. | Covers the user-visible lifecycle and ensures profile editing changes only the mutable field across a fresh login. |
| `ownerAddsMemberAcrossGroupsAndMemberCanViewBothMemberships` | Creates two groups, rejects an unknown email, adds a registered member, rejects a duplicate addition, then signs in as that member and verifies both groups and membership rows. | Exercises helpful failure responses, duplicate prevention, many-to-many group membership and member-visible group details through real screens. |
| `groupPersistsAfterReopenAndOnlyOwnerGetsEditAndDeleteControls` | Creates and reopens a group across account changes; checks member controls are hidden, owner controls are visible, then deletes the group and verifies it disappears from the list. | Checks persistent storage and role-specific UI affordances. The authoritative denial and deletion cleanup of child expenses/settlements are independently exercised by `functions/test/identity-groups.emulator.test.cjs` in the emulator suite. |

## JUnit white-box tests

File: `app/src/test/java/edu/usc/splitsmart/registration/IdentityFeatureWhiteBoxTest.java`. Run with the first Gradle command above.

## Feature directory convention

All white-box JUnit tests live under `app/src/test/java/edu/usc/splitsmart/`, grouped by feature (`registration`, `expense_management`, `groups`, and `settlements`). All black-box Espresso tests live under `app/src/androidTestLocal/java/edu/usc/splitsmart/` with the same feature subdirectories. Existing identity tests from other contributors remain in their current `identity` package; only these two Feature 1 contribution tests were moved.

| Test | Description | Result |
| --- | --- | --- |
| `emailNormalizationAndExactDomain` | Checks trimming/case normalization, subdomain rejection and lookalike suffix rejection. | PASS |
| `uscIdRequiresTenAsciiDigitsAndPreservesLeadingZeroCase` | Accepts ten ASCII digits with leading zeroes; rejects a short ID and Unicode numeral lookalikes. | PASS |
| `requiredNamesRejectBlankAndOverLimitValues` | Rejects whitespace-only and 101-character names; accepts the 100-character boundary. | PASS |
| `passwordBoundsAreInclusiveAtSixAnd4096Characters` | Rejects 5 characters, accepts 6 and 4096, and rejects 4097. | PASS |
| `groupDraftAllowsOmittedDescriptionButBoundsProvidedDescription` | Accepts an omitted description and the 1000-character boundary; rejects 1001 characters. | PASS |

## Coverage

Coverage criterion: JaCoCo line coverage and branch coverage for `IdentityValidation`, the white-box target class. The report was generated by `:app:createLocalDebugUnitTestCoverageReport` after running the unit suite.

- `IdentityValidation`: **100% line coverage** (18/18 lines) and **76% branch coverage** (29/38 branches).
- Whole Android local-debug unit-test source set: **24% instruction coverage** and **18% branch coverage**. This includes unrelated screens and features and is not a Feature 1-only metric.
- Report: `app/build/reports/coverage/test/local/debug/index.html`.

## Bugs found and fixes

No product defect was found in the requested Feature 1 flows during this contribution. Initial Espresso runs exposed test-harness assumptions: member controls can be below the visible viewport, profile recovery keeps the registration activity while switching its state, and owner controls can be visible in the layout while offscreen. The tests were corrected to scroll and dismiss the keyboard, assert the registration recovery state, and check effective visibility rather than screen display. These were test fixes, not product-code fixes.

## Team test-count status

This contribution adds five tests of each requested type. The repository now contains 88 local JUnit test methods, 17 Android instrumentation test methods (including the five Espresso tests above), and 35 host-side Firebase emulator integration tests. The assignment’s four-person minimum is 20 per type. The JUnit count exceeds that threshold, as does the combined black-box count. The roster and author emails were not supplied, so per-member attribution beyond this contribution cannot be verified. If the instructor counts only Android instrumentation methods as black-box cases, that narrower total is 17 and needs 3 more; the host emulator cases cover backend authorization, identity uniqueness and deletion behavior.
