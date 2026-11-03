# Architecture and development conventions

Contract baseline v1 adds immutable values and public interfaces to the existing
Android/Firebase foundation. [SCHEMA.md](SCHEMA.md) is canonical for fields and
representations; [CONTRACTS.md](CONTRACTS.md) is canonical for APIs, serialization,
transactions, retries and navigation. Identity/group management and X01 are implemented; other features remain contracts.

```text
Screen → ViewModel → Service interface → Repository interface
                                              ↑ implementation
                                       Firebase adapter
                                         ↙          ↘
                                  Firestore reads   Auth / callables
                                                       ↓
                                             Future trusted Functions
```

Screens display state and forward actions. ViewModels hold presentation state and
observe LiveData. Services own client validation and calculations. Repository
implementations isolate asynchronous I/O, serialization, caching and SDK error
mapping. Domain records, calculators, service contracts and repository interfaces
remain plain Java, independent of Android/Firebase. No service calls another
feature's service: Balance/Settlement share a future plain calculation helper,
and balances/history/editors consume a coherent LedgerSnapshot through LedgerRepository.

`config/SplitSmartApplication` and `AppContainer` remain the composition root.
Firebase initializes once before configured clients are exposed. FirebaseInitProvider
is removed, so local builds need no Google Services JSON/plugin. Local Auth,
Firestore and Functions route to 10.0.2.2; Firestore uses memory cache and the
existing diagnostic explicitly requests Source.SERVER. Infrastructure configuration
and the diagnostic test are the only SDK-import exceptions outside adapters.

AppContainer implements IdentityGroupServices with real Auth/Group services and
Firebase repositories. FeatureServices extends this available subset; unfinished
services are not stubbed. ViewModels receive services through FeatureFactory and
SavedStateHandle. FirebaseRepositoryContext centralizes account-scoped caches,
queued main-thread callbacks and at most three exact-payload transport retries.

Use FeatureScreen, FeatureViewModel, FeatureService, EntityRepository and
FirebaseEntityRepository names. Public services remain interfaces; name
an implementation DefaultExpenseService (for example) rather than replacing the
shared interface. Domain records use singular nouns and component accessors such
as amountCents(), not duplicate JavaBean getters. Java 17 records compile through
the existing pinned Android toolchain. Do not use their generated toString for
logging sensitive data; private User/ProfileDraft override it with a redacted value.

Methods are lowerCamelCase verbs; constants UPPER_SNAKE_CASE; packages lowercase.
Keep feature screens/ViewModels under their existing presentation packages.
Resources use feature-prefixed snake_case. Java uses four spaces; TypeScript/JSON
two. Shared collection copying is structural only. Pure calculation helpers will
live under domain/calculation when implemented; there are no algorithms there now.

Money is long cents with the v1 bounded decimal-string wire representation;
percentages are integer basis points. Creator and payer remain distinct. Dates are
LocalDate/YearMonth, timestamps Instant. Optional fields and list ordering are
fixed by SCHEMA.md. Callbacks, cancellation, read freshness and failures are fixed
by CONTRACTS.md; do not invent another error/result abstraction on a feature branch.

All product client writes remain denied. Callable operations must authorize
and validate independently because Admin SDK bypasses rules. Financial handlers
must use the common revision/receipt/full-ledger transaction protocol; a UI check
or revision field alone does not provide this guarantee. History/monthly summaries
are projections, not independently editable collections. The 500-transaction target
is a future performance measurement, not a product capacity cap.
