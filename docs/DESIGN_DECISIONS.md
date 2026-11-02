# Design sources and scope decisions

Reviewed inputs supplied by the user:

- `Team9 - SplitSmart Requirements_2.1.docx`: sections 3.2, 5 and 6 establish the
  five feature families, permissions, persistence and measurable quality targets.
- `Team9 - SplitSmart Requirements_2.2.docx`: sections 4–5 and embedded diagrams
  establish Java/XML Android, layered MVVM, repositories, domain types and callbacks.
- `SplitSmart_Feature_Catalog.pdf`: F01–F21, D01–D14 and suggested A–E ownership
  clarify the eventual product and the trusted backend boundary.
- `Project2.3_Implementation.pdf`: the final course deliverables are a demo, whole
  project ZIP and implementation PDF with requirements/design changes. This
  foundation is not that final implementation or report.

The user's infrastructure-only request controls this task. The catalog's page 10
and D11 say its larger foundation should implement identity/groups, shared models
and calculators. Those are **not implemented here**, because the current request
explicitly excludes all product features and financial algorithms. The project
assignment's instruction to implement the full app is context for later team work.

## Decisions retained for later features

| Catalog decision | Coordination implication |
| --- | --- |
| D01 | Native Android Java/XML, Material, ViewModel and LiveData; correct the old "web" preface in the final report. |
| D02 | Future successful registration includes completed profile and automatic sign-in; recover incomplete profiles. |
| D03–D04 | Payer participates among at least two distinct members; category required. |
| D05–D06 | Private 10-digit string USC ID and unique reservation; email/ID format checking does not prove USC identity. |
| D07 | Core history/summaries are group scoped; cross-group dashboard is optional future work. |
| D08 | Firestore supports integer values; Java long cents and integer basis points are the agreed client representation. |
| D09 | Preserve local calendar date; add authoritative timestamps and deterministic ID tie-break. |
| D10 | Share plain balance calculation across Balance/Settlement services rather than a service cycle. |
| D11 | Retain a TypeScript callable boundary for authoritative validation and mutations; this task supplies infrastructure only. |
| D12 | Private User and shared MemberProfile are distinct; no USC ID in member output. |
| D13 | Future deterministic splitting, settlement edit semantics, signed credits, idempotency and resumable deletion need coordinated implementation. |
| D14 | Original quality targets remain verification goals; this scaffold claims no application performance result. |

The Functions boundary changes 2.2's client-only mutation design because a
modified or concurrent client cannot be trusted to enforce shared financial
constraints. Services still perform local validation and previews. All cloud
rules remain denied until actual authorized features are reviewed. Server SDK
operations bypass rules and require independent authorization.

## Foundation-specific choices

- API 26 minimum provides native `java.time`; SDK 35 and the pinned AGP/Gradle
  pair keep tooling stable. Team approval is needed to change device support.
- Firebase initializes manually, avoiding a cloud JSON dependency in a clone.
- Local/cloud flavors isolate IDs and networking; cloud is explicit opt-in and
  local release is disabled. Local Firestore disk persistence is disabled to make
  emulator resets predictable, not to define future cloud durability behavior.
- A synthetic exact-path read in the local test APK proves SDK connectivity.
  Auth flows and open rules are unnecessary for that check.
- Package docs and draft schema/interfaces reserve ownership without inventing
  unconsumed classes. Model and contract agreement is the next team integration step.

These choices should be reflected in the final Project 2.3 change report once the
features exist. The supplied documents are left unchanged; optional X features
remain out of scope.
