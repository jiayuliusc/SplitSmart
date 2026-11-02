# Architecture and development conventions

The foundation implements only SDK configuration, manual dependency wiring and a
static screen. The following rules guide future implementation.

## Dependency direction

```text
Screen → ViewModel → Service → Repository interface
                                     ↑ implemented by
                              Firebase repository
                               ↙             ↘
                       Firestore reads   Auth / callable SDK
                                              ↓
                                 Future trusted Functions
```

Screens render state and forward actions. ViewModels hold presentation state and
expose LiveData; they do not calculate money or enforce business permissions.
Services own client-side validation and calculations. Repositories isolate SDK
types, mapping, async I/O, and persistence error translation. Domain models,
calculators, service APIs and repository interfaces remain plain Java.

`config/SplitSmartApplication` is the composition root.
`config/AppContainer` constructs configured SDK clients first and will wire
repositories/services as agreed implementations arrive. Configuration is an
explicit infrastructure exception to the SDK import boundary. The local Android
diagnostic is a second, test-only exception. The static placeholder needs no
ViewModel or service because it has no state or actions.

Firebase initialization happens once, synchronously in Application startup.
`FirebaseInitProvider` is removed; no Google Services plugin is used. In local
builds, Auth, Firestore and Functions are routed to `10.0.2.2` before any client
is exposed. Local Firestore uses memory cache so old emulator sessions cannot
appear to survive resets. The diagnostic explicitly uses `Source.SERVER`.

Future Firestore client **writes stay denied**. Callable functions must enforce
their own authentication, authorization and authoritative validation; Admin SDK
access bypasses rules. Client validation supports fast feedback but does not
secure the database or guarantee settlement limits under concurrency.

## Names and placement

Use `FeatureScreen`, `FeatureViewModel`, `FeatureService`, `EntityRepository` and
`FirebaseEntityRepository`. Domain classes use singular nouns. Methods are
lowerCamelCase verbs; constants are UPPER_SNAKE_CASE; packages are lowercase.
Use feature folders under `presentation`; group shared types by their actual
responsibility. XML filenames and resource IDs are snake_case with feature prefixes.
Java files use four spaces; TypeScript and JSON use two. No Kotlin/Compose
migration is planned. Prefer constructor injection over global singleton lookups.

## Async results and failures (proposal)

Preserve the design's plain Java callback contract (`onSuccess` / `onError`) rather
than leaking Firebase `Task`, snapshots, exceptions or Android LiveData into
services and domain. Specify exactly one terminal callback per operation. Decide
cancellation and executor semantics before implementing the first repository;
the proposed contract delivers on the main thread from adapters, while pure Java
services can be tested synchronously with fakes.

Use explicit error codes such as VALIDATION, UNAUTHENTICATED, PERMISSION_DENIED,
NOT_FOUND, CONFLICT, UNAVAILABLE and UNKNOWN, plus an optional field identifier.
Retain causes for safe diagnostics but map user-facing text at the presentation
boundary; do not expose raw backend messages, credentials or private IDs. A failed
fetch is not an empty result. Label cached data and pending writes accurately.
ViewModels preserve input and publish loading/success/error state; retry only
operations with agreed idempotency. No callbacks/error classes are implemented yet.

## Shared value conventions

- USD money is `long` **cents**, never `float` or `double`. `$10.00` is `1000`.
  Parsing, formatting, overflow checks and calculation algorithms are future work.
- Percentages use `int` **basis points**: `33.33%` is `3333`, total `10000`.
  Specify exact serialization and deterministic remainder allocation in shared
  Java/TypeScript fixtures before implementing calculations.
- Java `long` can exceed JavaScript's exact integer range. The proposed callable
  wire format uses decimal strings for cents and checked bounds/conversion. Do not
  blindly deserialize an arbitrary 64-bit value into a TypeScript `number`.
- IDs are strings. USC ID remains a 10-digit string including leading zeros; it
  is private profile data, never part of member projections or application logs.
- Immutable `localDate` uses calendar `yyyy-MM-dd`, generated from the creator's
  local date at creation. Month selection uses `yyyy-MM`. Do not use Java's
  week-based `YYYY` formatter. Keep authoritative `createdAt` and `updatedAt`
  timestamps separately; repository mapping converts SDK timestamps to plain
  Java `Instant`. Timestamp precision must be preserved for consistent sorting.
- `creatorId` is the actor who records an item; `payerId` is who paid. They are
  distinct for expenses. Preserve creator/date on edits. For future settlements,
  the authenticated debtor is initially payer and creator; store them separately.
- Categories: FOOD, HOUSING, TRANSPORTATION, ENTERTAINMENT, OTHER. Split methods:
  EQUAL, EXACT, PERCENTAGE. Do not persist localized display strings as enum keys.
- Positive pair balance means the other member owes the current user; negative
  means the current user owes them. Offsets are pairwise within one group. Do not
  silently add cross-group netting or rewrite old settlements after corrections.

Balance and Settlement services should share a plain `BalanceCalculator`, not
call one another. History and monthly summaries are read projections of source
records; settlements never count as spending. These conventions describe future
behavior and do not constitute implemented financial logic.
