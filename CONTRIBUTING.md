# Working on SplitSmart

This repository contains contract baseline v1 plus user/group management, X01, expenses and group balances. Read `docs/DESIGN_DECISIONS.md`
before implementing a requirement; the supplied catalog's larger foundation
backlog does not mean those product features exist.

## Branch and pull request workflow

The v1 definitions are merged into main. Begin feature work from updated main:

```bash
git switch main
git pull --ff-only
git switch -c feature/<workstream>-<short-description>
```

Use one small concern per branch. Keep `main` runnable. Do not force-push `main`.
Open a PR with the relevant F-number, behavior, checks and screenshots when UI
changes. Request at least one teammate review and the foundation maintainer for
shared files. Resolve CI failures before merging. Prefer squash merge; update your
branch against `main` before integration and tell collaborators before rewriting
a branch they also use. Never commit credentials, local SDK paths or emulator data.

## Five workstreams

Workstream owners A–E are selected by the team. **Jiayu Li (`jiayuliusc`) is the
single coordination maintainer** for changes crossing these boundaries. All paths below are relative to
`app/src/main/java/edu/usc/splitsmart`.

| Stream | Own feature code | Coordinate with |
| --- | --- | --- |
| A — identity and groups (F01–F06) | `presentation/identity`, `presentation/groups`; Auth/Group services, User/Group repositories; Functions identity/groups modules | All on member projections and group access; D on group deletion/concurrent writes |
| B — expenses and splitting (F07–F12) | `presentation/expenses`; future Expense service/repository and `domain/calculation/SplitCalculator` | C/D on signed amounts, immutable creator, revisions and transaction contracts |
| C — balances (F13–F14) | `presentation/balances`; Balance service and shared `domain/calculation/BalanceCalculator` | B/D on source snapshots, credits and authoritative debt validation |
| D — settlements (F15–F16) | `presentation/settlements`; future Settlement service/repository and backend module | B/C before any financial write implementation; A on membership |
| E — history and summaries (F17–F21) | `presentation/history`; future History service and projection/filter value classes | B/D on dates and transaction data; A on group navigation |

Place services in `service` (add feature subpackages when useful), shared models
in `domain/model`, repository interfaces in `data/repository`, and SDK adapters in
`data/firebase`. Keep screen layouts under `res/layout` with feature prefixes such
as `screen_expense_form.xml`; similarly prefix feature string IDs. Add only actual
classes needed by your feature. Feature tests arrive with feature implementation.

Use the existing configured clients through repository construction in
`config/AppContainer`; do not initialize Firebase again. Screens use ViewModels,
ViewModels use services, and services use repository interfaces. Plain Java domain
types cannot import Android/Firebase. Refer to `docs/ARCHITECTURE.md`.

## Coordinate shared changes first

Before changing a shared contract, propose the concrete signature/field/rule,
rationale and affected workstreams in the PR or issue. Affected owners must agree
before other branches rely on it. Update `docs/SCHEMA.md` and `docs/CONTRACTS.md`
with code, migration implications and the validation plan. The public source types and SCHEMA.md/CONTRACTS.md are baseline v1; do not create
a competing proposal or change signatures privately.

Shared files requiring coordination:

- `app/build.gradle`, root build/settings files, Gradle wrapper/properties,
  `package*.json`, `functions/package.json`, `.nvmrc` and CI workflows.
- `config/*`, the manifest, the login launcher, shared screen/session base classes and themes.
- `domain/*`, `data/repository/*`, common callbacks/errors and calculator contracts.
- Firebase configuration, rules, indexes, Functions exports, region and schema.
- Cross-feature resources and all coordination documents.

Add dependencies only after explaining why existing tools are insufficient, pin
the version centrally in the relevant build file, regenerate the npm lockfile if
needed, and run build/lint. Follow the existing explicit-Activity navigation approach and the frozen
NavigationContract keys; a new navigation library is a separate coordinated change. Add navigation in a dedicated integration PR;
feature screens can be built independently first. Never pass SDK snapshots or
private USC IDs through screen arguments.

The revision, idempotency, numeric, rounding and settlement replacement contracts
are now fixed in CONTRACTS.md. Implement them rather than reopening separate
alternatives on each branch. B/C/D must review changes to that protocol together.
A must independently authorize all profile/group operations.
Do not loosen rules broadly to unblock another branch.

## Shared ownership

| Shared area | Primary owner | Required coordination |
| --- | --- | --- |
| User, MemberProfile, Session, ProfileDraft, Group/GroupDraft and auth/group contracts | A | Maintainer; other consumers for field/signature changes |
| Expense, ExpenseDraft, ExpenseShare, SplitInput, Category, SplitMethod | B | C/D/E plus maintainer |
| PairBalance, BalanceSummary, LedgerSnapshot and LedgerRepository/getGroupLedger | C | B/D/E plus maintainer |
| Settlement, SettlementDraft and settlement contracts | D | B/C/E plus maintainer |
| HistoryItem, HistoryFilter, HistorySort, MonthlySummary, TransactionType | E | B/C/D plus maintainer |
| Callback/error/read/mutation wrappers, wire maps, fixtures and versioning | Jiayu Li | All affected workstreams |
| Common backend receipt/cleanup infrastructure | Jiayu Li | A/B/C/D; functions/src/infrastructure receipts and groups/cleanup; financial ledger runner remains future work |
| Composition root, FeatureServices, navigation keys/host and shared resources | Jiayu Li | Feature owners integrating routes |
| Product security rules/indexes | A, coordinated by Jiayu Li | Every owner whose path/query changes; rules remain denied until reviewed implementation |

B and D must reuse the receipt infrastructure and implement the documented common
financial transaction runner before adding financial handlers. Identity/group
handlers do not implement financial validation.
Feature-private helpers, ViewModel state, layouts and test fakes are owned by each
stream. Production fakes or throwing service stubs must not be wired into navigation.

## Checks and merge expectations

```bash
npm ci
npm run functions:build
./gradlew :app:assembleLocalDebug :app:testLocalDebugUnitTest :app:lintLocalDebug
npm run check:contracts
npm run check:emulators
```

Run `npm run check:android` with an AVD for changes to SDK setup, networking,
manifest or the diagnostic. It starts its own Firebase suite, so stop other suites
first. Add focused unit, instrumentation or rules tests with the behavior being
implemented; no tests are required for nonexistent business features. Report
checks not run honestly. Product features need reviewed authorization and failure
states before merge, not just a screenshot. Keep Windows scripts working.
