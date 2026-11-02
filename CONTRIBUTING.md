# Working on SplitSmart

This foundation contains infrastructure only. Read `docs/DESIGN_DECISIONS.md`
before implementing a requirement; the supplied catalog's larger foundation
backlog does not mean those product features exist.

## Branch and pull request workflow

After the owner publishes the foundation and its smoke check passes:

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

These are suggested boundaries, not assignments of named teammates. Agree on who
owns each stream. All paths below are relative to
`app/src/main/java/edu/usc/splitsmart`.

| Stream | Own feature code | Coordinate with |
| --- | --- | --- |
| A — identity and groups (F01–F06) | `presentation/identity`, `presentation/groups`; future Auth/Group services, User/Group repositories; Functions identity/groups modules | All on member projections and group access; D on group deletion/concurrent writes |
| B — expenses and splitting (F07–F12) | `presentation/expenses`; future Expense service/repository and `domain/calculation/SplitCalculator` | C/D on signed amounts, immutable creator, revisions and transaction contracts |
| C — balances (F13–F14) | `presentation/balances`; future Balance service and shared `domain/calculation/BalanceCalculator` | B/D on source snapshots, credits and authoritative debt validation |
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
with code, migration implications and the validation plan. Draft prose is not
permission to implement incompatible versions independently.

Shared files requiring coordination:

- `app/build.gradle`, root build/settings files, Gradle wrapper/properties,
  `package*.json`, `functions/package.json`, `.nvmrc` and CI workflows.
- `config/*`, the manifest, `FoundationScreen`, themes and future navigation host/graph.
- `domain/*`, `data/repository/*`, common callbacks/errors and calculator contracts.
- Firebase configuration, rules, indexes, Functions exports, region and schema.
- Cross-feature resources and all coordination documents.

Add dependencies only after explaining why existing tools are insufficient, pin
the version centrally in the relevant build file, regenerate the npm lockfile if
needed, and run build/lint. Avoid a navigation library until the team agrees on
the common host and route arguments. Add navigation in a dedicated integration PR;
feature screens can be built independently first. Never pass SDK snapshots or
private USC IDs through screen arguments.

B/C/D must agree on authoritative group revisions, idempotency, rounding fixtures,
safe numeric bounds and settlement replacement semantics before implementing
financial persistence. A must independently authorize all profile/group operations.
Do not loosen rules broadly to unblock another branch.

## Checks and merge expectations

```bash
npm ci
npm run functions:build
./gradlew :app:assembleLocalDebug :app:lintLocalDebug
npm run check:emulators
```

Run `npm run check:android` with an AVD for changes to SDK setup, networking,
manifest or the diagnostic. It starts its own Firebase suite, so stop other suites
first. Add focused unit, instrumentation or rules tests with the behavior being
implemented; no tests are required for nonexistent business features. Report
checks not run honestly. Product features need reviewed authorization and failure
states before merge, not just a screenshot. Keep Windows scripts working.
