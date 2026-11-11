# Shared contracts — baseline v1

This document and [SCHEMA.md](SCHEMA.md) define contract baseline v1. Identity/group
management and X01 now implement their listed services, repositories, codecs and
seven callables. Expense, balance, settlement and history services implement their v1 interfaces.
The additive X01 auth method and composition subset below do not change stored or wire schemas.
See [implementation map](USER_GROUP_IMPLEMENTATION.md) for scope and verification.

## Source of truth

| Boundary | Actual definitions |
| --- | --- |
| Domain values/enums | [`domain/model`](../app/src/main/java/edu/usc/splitsmart/domain/model), constructor inventory in SCHEMA.md |
| Async results/errors | [`domain/contract`](../app/src/main/java/edu/usc/splitsmart/domain/contract) |
| Repository interfaces | [`data/repository`](../app/src/main/java/edu/usc/splitsmart/data/repository) |
| Service interfaces | [`service`](../app/src/main/java/edu/usc/splitsmart/service) |
| Java callable payloads | [`CallableRequests`](../app/src/main/java/edu/usc/splitsmart/data/contract/CallableRequests.java), [`WireModels`](../app/src/main/java/edu/usc/splitsmart/data/contract/WireModels.java), [`CallableName`](../app/src/main/java/edu/usc/splitsmart/data/contract/CallableName.java) |
| Matching TypeScript payloads | [`functions/src/contracts/v1.ts`](../functions/src/contracts/v1.ts), `Requests` and `Responses` maps keyed by callable name |
| Shared serialization examples | [`contracts/v1/fixtures.json`](../contracts/v1/fixtures.json), used by both language checks |
| Financial acceptance examples | [`financial-expectations.json`](../contracts/v1/financial-expectations.json); expectations, not algorithm test results |

Java `WireModels` uses strings for cents/revisions and a plain Timestamp record;
domain objects use `long`, `Instant` and `LocalDate`. Repository adapters must map
explicitly between them. Never hand a domain record directly to the Firebase
callable encoder, or serialize an SDK snapshot into a service result. Gson is a
test dependency only. IdentityCodec explicitly maps supported user/group SDK and callable values.

## Asynchronous and read contract

Every repository/service operation returns `AsyncOperation` and uses
`Callback<T>.onSuccess(T)` or `onError(AppError)`. Callbacks are queued on the
Android main thread, **never inline**, even for a validation error or cache hit.
Implementers inject an executor/dispatcher at the composition root so services
stay plain Java. Exactly one terminal callback occurs unless cancelled. `Void`
success is permitted only for signOut and sendPasswordResetEmail. Reset success
acknowledges processing the reset request; it does not prove account existence or
mail delivery. Normalize Auth's unknown-user response to the same neutral success
used for an accepted request, while preserving provider/transport failures. The
confirmation must not disclose whether an email is registered.

`cancel()` is idempotent, may be called from any thread, detaches listeners, and
suppresses callbacks that have not started when cancellation returns. A callback
already running can finish. Cancellation does not undo a server write. Preserve
its request ID and immutable payload to discover the result by retrying. Scope
ViewModel-owned operations to `onCleared()`; screen observers detach with their
view lifecycle. On session change/sign-out, cancel private work and discard all
session-scoped cache/state. Late results from an old session must never appear in
a new user's screen.

`observeSession(SessionObserver)` queues an initial Session, then ordered changes;
`onError` terminates observation. It is the only nonterminal stream; its returned
AsyncOperation cancels it. `getSession` is a one-shot snapshot of the SDK session,
not proof that a profile exists. AuthRepository signUp/signIn/signOut wrap Firebase
Auth, not callables. AuthService.register coordinates signUp then completeProfile;
its request ID applies to profile completion only. Auth account creation is not
atomic with Firestore and is not magically idempotent. Recover a partial account
by checking/signing into the session and retrying profile completion; never report
full registration success before both steps complete. Password confirmation and
password draft retention are private workstream A details, not shared stored types.

`ReadPolicy.FRESH` requires a new server read and fails on unavailable transport;
`CACHE_ONLY` does no network I/O and fails `CACHE_MISS` if a complete snapshot for
the current UID/scope is absent. No automatic fallback. `ReadResult.source` reports
SERVER or CACHE. An authorized missing single record returns NOT_FOUND; an empty
list is a successful immutable empty list. Read adapters reject unsupported stored
schema versions with INTERNAL and do not silently coerce them.

C/E calculations and D/B editors use `LedgerRepository.getLedger` (exposed by
ExpenseService/SettlementService as `getLedger`). A fresh ledger comes from the
`getGroupLedger` callable, which uses the same transaction read strategy below
without writes. CACHE_ONLY may reuse an entire previous ledger for the current
user/group, never assemble separate cached expense and settlement queries. A
single-record detail read is not a coherent edit snapshot: edit/delete actions
must use the record and expectedRevision from the **same** ledger snapshot.
`GroupService.getGroup` supplies revision for group management actions.

List ordering and missing/optional field semantics are fixed in SCHEMA.md. New
remote data arriving after a read can make it stale; that is handled by the
expected revision and explicit refresh, not by pretending a snapshot is live.

## Exact interfaces

The declarations below mirror the compiling sources. `Callback`, `AsyncOperation`,
`ReadPolicy`, `ReadResult` and `MutationResult` come from `domain.contract`; other
value types come from `domain.model`. Source files remain authoritative for Java
syntax. Identity/group methods now have real implementations; expense, balance, settlement and
history/summary methods also have implementations.

```java
interface AuthRepository {
    AsyncOperation getSession(Callback<Session> callback);
    AsyncOperation observeSession(SessionObserver observer);
    AsyncOperation signUp(String email, String password, Callback<Session> callback);
    AsyncOperation signIn(String email, String password, Callback<Session> callback);
    AsyncOperation signOut(Callback<Void> callback);
    AsyncOperation sendPasswordResetEmail(String email, Callback<Void> callback);
}
```

```java
interface UserRepository {
    AsyncOperation getCurrentProfile(ReadPolicy policy, Callback<ReadResult<User>> callback);
    AsyncOperation completeProfile(ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback);
    AsyncOperation updateName(String name, String requestId, Callback<MutationResult<User>> callback);
}
```

```java
interface GroupRepository {
    AsyncOperation getGroupsForCurrentUser(ReadPolicy policy, Callback<ReadResult<List<Group>>> callback);
    AsyncOperation getGroup(String groupId, ReadPolicy policy, Callback<ReadResult<Group>> callback);
    AsyncOperation getMemberProfiles(String groupId, ReadPolicy policy, Callback<ReadResult<List<MemberProfile>>> callback);
    AsyncOperation createGroup(GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback);
    AsyncOperation addMember(String groupId, long expectedRevision, String email, String requestId, Callback<MutationResult<Group>> callback);
    AsyncOperation updateGroup(String groupId, long expectedRevision, GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback);
    AsyncOperation deleteGroup(String groupId, long expectedRevision, String requestId, Callback<MutationResult<GroupDeletion>> callback);
}
```

```java
interface ExpenseRepository {
    AsyncOperation getExpense(String groupId, String expenseId, ReadPolicy policy, Callback<ReadResult<Expense>> callback);
    AsyncOperation createExpense(String groupId, long expectedRevision, ExpenseDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Expense>> callback);
    AsyncOperation updateExpense(String groupId, long expectedRevision, String expenseId, ExpenseDraft draft, String requestId, Callback<MutationResult<Expense>> callback);
    AsyncOperation deleteExpense(String groupId, long expectedRevision, String expenseId, String requestId, Callback<MutationResult<DeletedRecord>> callback);
}
```

```java
interface SettlementRepository {
    AsyncOperation getSettlement(String groupId, String settlementId, ReadPolicy policy, Callback<ReadResult<Settlement>> callback);
    AsyncOperation createSettlement(String groupId, long expectedRevision, SettlementDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Settlement>> callback);
    AsyncOperation updateSettlement(String groupId, long expectedRevision, String settlementId, SettlementDraft draft, String requestId, Callback<MutationResult<Settlement>> callback);
    AsyncOperation deleteSettlement(String groupId, long expectedRevision, String settlementId, String requestId, Callback<MutationResult<DeletedRecord>> callback);
}
```

```java
interface LedgerRepository {
    AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback);
}
```

```java
interface AuthService {
    AsyncOperation getSession(Callback<Session> callback);
    AsyncOperation observeSession(SessionObserver observer);
    AsyncOperation register(String email, String password, ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback);
    AsyncOperation signIn(String email, String password, Callback<Session> callback);
    AsyncOperation signOut(Callback<Void> callback);
    AsyncOperation sendPasswordResetEmail(String email, Callback<Void> callback);
    AsyncOperation getCurrentProfile(ReadPolicy policy, Callback<ReadResult<User>> callback);
    AsyncOperation completeProfile(ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback);
    AsyncOperation updateName(String name, String requestId, Callback<MutationResult<User>> callback);
}
```

```java
interface GroupService {
    AsyncOperation getGroupsForCurrentUser(ReadPolicy policy, Callback<ReadResult<List<Group>>> callback);
    AsyncOperation getGroup(String groupId, ReadPolicy policy, Callback<ReadResult<Group>> callback);
    AsyncOperation getMemberProfiles(String groupId, ReadPolicy policy, Callback<ReadResult<List<MemberProfile>>> callback);
    AsyncOperation createGroup(GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback);
    AsyncOperation addMember(String groupId, long expectedRevision, String email, String requestId, Callback<MutationResult<Group>> callback);
    AsyncOperation updateGroup(String groupId, long expectedRevision, GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback);
    AsyncOperation deleteGroup(String groupId, long expectedRevision, String requestId, Callback<MutationResult<GroupDeletion>> callback);
}
```

```java
interface ExpenseService {
    AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback);
    AsyncOperation previewSplit(ExpenseDraft draft, Callback<List<ExpenseShare>> callback);
    AsyncOperation getExpense(String groupId, String expenseId, ReadPolicy policy, Callback<ReadResult<Expense>> callback);
    AsyncOperation createExpense(String groupId, long expectedRevision, ExpenseDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Expense>> callback);
    AsyncOperation updateExpense(String groupId, long expectedRevision, String expenseId, ExpenseDraft draft, String requestId, Callback<MutationResult<Expense>> callback);
    AsyncOperation deleteExpense(String groupId, long expectedRevision, String expenseId, String requestId, Callback<MutationResult<DeletedRecord>> callback);
}
```

```java
interface SettlementService {
    AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback);
    AsyncOperation getSettlement(String groupId, String settlementId, ReadPolicy policy, Callback<ReadResult<Settlement>> callback);
    AsyncOperation createSettlement(String groupId, long expectedRevision, SettlementDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Settlement>> callback);
    AsyncOperation updateSettlement(String groupId, long expectedRevision, String settlementId, SettlementDraft draft, String requestId, Callback<MutationResult<Settlement>> callback);
    AsyncOperation deleteSettlement(String groupId, long expectedRevision, String settlementId, String requestId, Callback<MutationResult<DeletedRecord>> callback);
}
```

```java
interface BalanceService {
    AsyncOperation getBalances(String groupId, ReadPolicy policy, Callback<ReadResult<BalanceSummary>> callback);
}
```

```java
interface HistoryService {
    AsyncOperation getHistory(String groupId, HistoryFilter filter, ReadPolicy policy, Callback<ReadResult<List<HistoryItem>>> callback);
    AsyncOperation getMonthlySummary(String groupId, YearMonth month, ReadPolicy policy, Callback<ReadResult<MonthlySummary>> callback);
}
```

Create methods receive an automatically captured LocalDate explicitly so retry
payloads remain stable across midnight. Update methods omit it. The ViewModel
keeps the requestId, date, draft and expectedRevision unchanged until a definite
outcome; it generates a new request only for a new user-approved intent.
`MutationResult<T>(requestId, groupRevision, value)` includes a revision for every
group-scoped mutation and null only for profile mutations. Deleting a record returns
`DeletedRecord(recordId)`. Deleting a group returns `GroupDeletion(groupId, DELETING)`:
this acknowledges durable deletion acceptance, not completion of all cleanup.

## Callable names, payloads and ownership

All functions use the existing `us-central1` client configuration. Each request and
success response includes `schemaVersion: 1`. The actual payload is passed as the
SDK callable's data; do not add a second `data` wrapper. The SDK owns its HTTP
protocol envelope. Required fields are exactly the fields in the linked request
records and TS interfaces. No request has ownerId, creatorId, caller UID, stored
shares, stored timestamps or currency. A groupId scopes an operation, not authority.

| Callable | Owner | Request type | Success type |
| --- | --- | --- | --- |
| `completeProfile` | A | `CompleteProfileRequest` | `ProfileMutationResponse` |
| `updateProfileName` | A | `UpdateProfileNameRequest` | `ProfileMutationResponse` |
| `createGroup` | A | `CreateGroupRequest` | `GroupMutationResponse<Group>` |
| `addGroupMember` | A | `AddGroupMemberRequest` | `GroupMutationResponse<Group>` |
| `updateGroup` | A | `UpdateGroupRequest` | `GroupMutationResponse<Group>` |
| `deleteGroup` | A | `DeleteGroupRequest` | `GroupMutationResponse<GroupDeletion>` |
| `getGroupMemberProfiles` | A | `GetGroupMemberProfilesRequest` | `MembersResponse` |
| `getGroupLedger` | C | `GetGroupLedgerRequest` | `LedgerResponse` |
| `createExpense` | B | `CreateExpenseRequest` | `GroupMutationResponse<Expense>` |
| `updateExpense` | B | `UpdateExpenseRequest` | `GroupMutationResponse<Expense>` |
| `deleteExpense` | B | `DeleteExpenseRequest` | `GroupMutationResponse<DeletedRecord>` |
| `createSettlement` | D | `CreateSettlementRequest` | `GroupMutationResponse<Settlement>` |
| `updateSettlement` | D | `UpdateSettlementRequest` | `GroupMutationResponse<Settlement>` |
| `deleteSettlement` | D | `DeleteSettlementRequest` | `GroupMutationResponse<DeletedRecord>` |

`completeProfile` takes `profile: {name, uscId}`; authenticated email supplies the
email. `updateProfileName` takes name. Group create/update take a full GroupDraft.
`addGroupMember.email` is trimmed, lowercase and an exact `usc.edu` domain.
Member responses contain authorized group members only, never a user directory.
The backend derives new group owner, expense creator and settlement creator
from verified authentication; a settlement payer is the optional `payerId` or the actor,
and the actor must be the payer or payee. It must independently enforce membership, ownership,
creator rights, profile readiness and every authoritative validation rule.

All existing-group mutations require `groupId`, `expectedRevision`, and a requestId.
Financial creates add localDate and a full draft; updates add the stored child ID
and a full draft; deletes add only the stored child ID. Read callables require just
schemaVersion/groupId and return a complete members list or ledger snapshot.
There are no callable balance/history/summary algorithms: C/E compute projections
from the coherent ledger in Java.

An example createExpense payload (no actor or computed allocation):

```json
{"schemaVersion":1,"requestId":"00000000-0000-4000-8000-000000000009","groupId":"group-1","expectedRevision":"1","localDate":"2026-11-02","draft":{"title":"Lunch","amountCents":"3000","payerId":"A","participantIds":["A","B","C"],"splitMethod":"EQUAL","category":"FOOD","splitInputs":[]}}
```

Success is `GroupMutationResponse<T> {schemaVersion, requestId, groupRevision, value}`
for group operations, or `ProfileMutationResponse {schemaVersion, requestId, value}`
for profile operations. These are distinct wire record types.
The receipt stores that complete response, including immutable generated IDs and
timestamps. `groupRevision` is omitted for profile operations; it is required for
group operations, including group creation (revision `"1"`). Omitted note/description
clears that value on update. Explicit null, extra fields, invalid enum/date/number
encoding, or an unsupported version is VALIDATION. See SCHEMA.md for exact ranges.

## Errors and retries

`AppError(code, message, field, retryable)` has a sanitized diagnostic message and
optional field path (for example `draft.amountCents`). ViewModels map codes to user
text; never surface raw SDK messages or private profile data. Business errors use
Firebase `HttpsError` with `details: WireModels.ErrorDetails` /
`v1.ErrorDetails {schemaVersion:1, code, field?, retryable}`. The standard transport
message is not another data property. Adapters map transport failures without
trusted details to the corresponding safe AppError.

| Contract code | Firebase HttpsError code | Action |
| --- | --- | --- |
| VALIDATION | invalid-argument | Correct input; new intent/requestId. |
| OUT_OF_RANGE | out-of-range | Correct amount or supported workload; never round/clamp. |
| UNAUTHENTICATED | unauthenticated | Restore session; do not resend as another actor. |
| PERMISSION_DENIED | permission-denied | Stop; refresh access/session. |
| NOT_FOUND | not-found | Refresh or recover missing profile; no null-as-success. |
| CACHE_MISS | local only | Explicitly request FRESH if desired. |
| ALREADY_EXISTS | already-exists | Correct identity/membership or recover partial Auth registration. |
| CONFLICT | aborted | Refresh snapshot and ask user to retry with new expectedRevision/requestId. |
| IDEMPOTENCY_MISMATCH | failed-precondition | Bug or reused key: never overwrite the original receipt. |
| GROUP_DELETING | failed-precondition | Stop new work and leave group screens. |
| RESOURCE_EXHAUSTED | resource-exhausted | No blind retry; reduce request size or coordinate capacity work. |
| UNAVAILABLE | unavailable | Recoverable; resend exact payload with same key after backoff. |
| DEADLINE_EXCEEDED | deadline-exceeded | Outcome may be unknown; resend exact payload with same key. |
| INTERNAL | internal | Stop automatic retries; preserve request ID for investigation. |

Only UNAVAILABLE/DEADLINE_EXCEEDED have `retryable=true`. Exhausted server contention
retries map to UNAVAILABLE; a known stale expectedRevision maps to CONFLICT.
No client loop retries silently or changes an intent. Exponential backoff/jitter
is private implementation, bounded to three automatic retries; then surface the
recoverable error. A user retry still keeps the original key/payload for an unknown
outcome. A definite conflict/input correction is a new intent with a new key.

## Financial mutation protocol (required future implementation)

The protocol below applies to every expense/settlement create, edit and delete.
It is implementable with the pinned `firebase-admin` 13.5.0 and lockfile-resolved
`@google-cloud/firestore` 7.11.6. Inspection of its `types/firestore.d.ts` confirmed
`Transaction.get(Query)`, `get(DocumentReference)`, `create`, `update`, `delete`
and `runTransaction(..., {maxAttempts: 5})`. This baseline does not execute or
prove a financial handler; those implementations need contention and retry tests.

1. Verify authentication, parse v1 JSON strictly and check request ID syntax.
   Reject caller-supplied actors or immutable fields. Generate a new child ID once
   outside the transaction for creates. Keep the full immutable request in memory.
   Financial validation uses current authoritative records, not client shares or
   a client balance. Registration/Auth and external I/O never run in the transaction.
2. Call `db.runTransaction(callback, {maxAttempts: 5})`. First read
   `mutationReceipts/{auth.uid}/requests/{requestId}`. Request IDs are lowercase
   UUID v4 and unique **per actor across all operations and groups**. If present,
   compare callable name and exact canonical payload; mismatch returns
   IDEMPOTENCY_MISMATCH. A match returns the original stored success/error, without
   recomputing financial state or incrementing revision. A replay is authorized to
   the same authenticated actor's own receipt, including after group deletion.
3. For a new request, read `groups/{groupId}` and `users/{auth.uid}`. Validate profile,
   membership, ACTIVE status and creator permission; compare expectedRevision to
   group.revision. The group is the common concurrency guard. A stale revision is
   CONFLICT; a deleting group is GROUP_DELETING. Nonmembers cannot bypass these
   checks through a guessed child ID. Existing child IDs must belong to this group.
4. Read **both complete collections inside this same transaction**, before writes:
   `tx.get(db.collection('groups/'+groupId+'/expenses').orderBy(FieldPath.documentId()))`
   and the corresponding settlements query. No limit(500), pagination outside the
   transaction, independent repository reads, cached data, or editable aggregate
   counters. These snapshots plus group membership/status/revision and the actor's
   profile are authoritative. Select the existing record from these snapshots for edit/
   delete and edit/delete and validate its immutable creator/payer/payee/date metadata.
5. Compute/validate the proposed resulting ledger with exact arithmetic. Backend
   recalculates expense shares, validates all references, and enforces positive
   settlement amount <= authoritative pairwise debt. For a settlement edit, remove
   the original from the snapshot first, then validate/apply the replacement; an
   unchanged valid payment must remain editable. Expense corrections may produce
   credits; never rewrite historical settlements to hide them.
6. Only after **all reads**: write/create/delete one child record, update group
   revision to old+1 and updatedAt, and create the success receipt in the **same
   transaction**. Choose server-clock microsecond timestamps for the new response
   within the attempt; persist the exact same values in record and receipt. Do not
   return unresolved serverTimestamp transforms. The callback may rerun; no side
   effects or successful UI result occur until `runTransaction` resolves.
7. A deterministic domain rejection after authenticated shape parsing is saved as
   an ERROR receipt in a transaction, without changing financial records/revision.
   Return a tagged outcome from the transaction callback, commit the receipt, and
   only then translate the saved error to HttpsError outside the transaction;
   throwing inside the callback would roll the receipt back. This freezes its
   payload and repeated error. Do not persist transient
   UNAVAILABLE/DEADLINE_EXCEEDED errors or pre-auth/invalid-shape failures. On an
   ambiguous commit, retry the same key and inspect its receipt; never assume a
   timeout means no commit. A mismatch must not replace an existing receipt.

The canonical payload is the JSON object
`{"operation": callableName, "request": validatedRequestWithoutRequestId}`.
Recursively sort object keys by Unicode code point, retain array order, omit
whitespace, use ECMAScript JSON.stringify string escaping, and encode integer
numbers in decimal form. Persist these UTF-8 canonical bytes as a string and their SHA-256
hex hash. Compare the stored canonical string as well as the hash; semantic changes
in optional fields, dates, scope, revision or array order are different payloads.
Receipts have no TTL in v1; deleting a group must not delete them. Never retry a
known failure with changed input under the old key. Receipt paths are client-denied.
Profile/group mutations use the same receipt conventions;
profile completion atomically claims the private USC ID with the profile/receipt.

Firestore supports transactional query reads and serializable isolation; document
reads precede writes. The common group read/update additionally serializes **all**
participating financial writers, including creates that add new query matches.
No alternate Admin writer may bypass this guard. See the [server transaction API](https://docs.cloud.google.com/nodejs/docs/reference/firestore/latest/firestore/transaction)
and [transaction isolation documentation](https://firebase.google.com/docs/firestore/transaction-data-contention).

`getGroupLedger` uses a read-write transaction with the same group/profile checks
and complete queries, `maxAttempts: 5`, but **zero writes** and no revision bump.
This avoids mixing independently fetched versions and does not request a historical
readTime. The result carries the revision and memberIds read alongside those records,
so zero-balance members do not require a second inconsistent membership read. Authorization
failures return no ledger. A response-size or capacity failure is explicit, never a
partial ledger. Ordinary group/member reads remain repository-owned SDK reads or
the scoped member callable; callers must obey freshness and privacy semantics.

### Concurrent payments and group deletion

For debt 600 cents, two 400-cent requests at revision 7: the first commits revision
8. The second must see/retry against the changed group and returns CONFLICT with
no payment. After refresh, a **new** 400-cent request at revision 8 fails VALIDATION
because only 200 cents remain. Both original payments cannot commit. Even a caller
that supplies a later revision must pass the authoritative debt check.

Group updates, membership additions and delete acceptance also read the group and
increment the same revision, with an expectedRevision guard. `deleteGroup` atomically
marks DELETING, increments revision, writes `groupDeletionJobs/{groupId}` and the
acceptance receipt. Later financial operations fail GROUP_DELETING. Competing
financial/delete operations cannot both use the old revision. Cleanup runs only
under the DELETING marker, in resumable transactions of at most 200 child deletions
per batch, each re-reading parent/job and advancing the parent revision and job
updatedAt. Remaining child records represent cleanup progress; no cursor is needed. This is a
batch size, not a product transaction-count limit. It repeatedly queries bounded
expense/settlement batches, then verifies both collections empty before deleting
the parent and marking the job DELETED. Keep the job tombstone and actor receipts;
never reuse a group ID. Another delete key against a deleting group is
GROUP_DELETING; replaying the accepted key returns its original DELETING response.

The marker blocks child creation during cleanup. Merely deleting a parent would
leave subcollections behind; the [official deletion guidance](https://firebase.google.com/docs/firestore/manage-data/delete-data)
requires explicit child cleanup. `functions/src/groups/cleanup.ts` implements this worker. The retry-enabled
`cleanupDeletedGroup` Firestore creation trigger resumes the durable job. Each
delivery attempts at most 100 batches; remaining work throws for redelivery.
Monitor failed deliveries and resume retained DELETING jobs if the platform retry
window expires. No financial create/edit/delete handler is exported.

### Scaling and failure boundary

This is an O(group ledger size) server transaction, chosen for the documented
small-group workload. **500 transactions is a performance-test target, not a
product limit.** Larger groups remain valid if the full operation fits resources.
Initial future handlers should use 512 MiB and a 60-second request deadline, with
at most five transaction attempts; define measured operational budgets before
cloud rollout. A capacity failure is RESOURCE_EXHAUSTED, or DEADLINE_EXCEEDED for
an ambiguous timed-out commit. Do not compute against a truncated ledger or fall
back to unguarded writes. Capacity changes or a maintained aggregate design require
a coordinated contract revision and migration, not a hidden hard cap at 500.

Firestore imposes document/request/time limits (including 1 MiB documents, 10 MiB
API requests and a 270-second transaction / 60-second idle limit). Receipts and
embedded shares must fit document limits; oversize writes fail atomically. A
Functions deadline can occur sooner and does not prove a commit was rolled back.
See [official limits](https://firebase.google.com/docs/firestore/quotas). This design
has been checked against the SDK surface and documented guarantees, not benchmarked
or validated under real financial contention yet.

## Shared financial expectations

The JSON expectations file records these required future outcomes. It does not
contain an implementation of splitting, balance aggregation or settlement validation.

| Scenario | Required result |
| --- | --- |
| A pays 3000 cents equally for A/B/C | Shares 1000 each; B and C each owe A 1000. A's positive pair balances are B=1000, C=1000. |
| B settles 400 cents with A | B owes A 600; C still owes A 1000. A receives total 1600. |
| Concurrent 400-cent settlements against debt 600 at one revision | One success, one CONFLICT; remaining debt 200; no double commit. |
| Monthly summary of the 3000-cent expense plus 400-cent settlement | Expense count 1, total 3000, FOOD 3000; all other categories zero. |
| Equal 1000-cent split across sorted A/B/C | A=334, B=333, C=333, total exactly 1000. |

Equal remainder cents go to ascending participant UID. Percentage allocation uses
largest fractional remainder, with UID as tie-break; inputs total 10000 basis
points. These are frozen expectations for later calculator implementation, not
algorithms added to the baseline.

## Navigation and dependency wiring

Identity/group Activities use no navigation library. Retain explicit Android
Activity intents and Activity Result APIs as features arrive; do not introduce a
competing global navigator. [`NavigationContract`](../app/src/main/java/edu/usc/splitsmart/presentation/navigation/NavigationContract.java)
freezes keys: GROUP_ID, EXPENSE_ID, SETTLEMENT_ID are String extras, and RESULT_REQUEST_ID /
RESULT_GROUP_ID are String results; RESULT_GROUP_REVISION is a `long` result.
A child ID always travels with groupId. No actor UID, User object, private profile,
Firebase object or whole ledger goes in an intent. Missing scope is an error,
never an implicit choice of the last-opened group.

ViewModels obtain Session through injected AuthService (getSession/observeSession),
and scoped IDs from the host's saved arguments. Persist IDs/draft state through
SavedStateHandle; reacquire authoritative data after process death. Route an
unauthenticated session to the sign-in flow and a missing private profile
to profile completion. Backend authorization remains mandatory even with a valid
local route.

[`IdentityGroupServices`](../app/src/main/java/edu/usc/splitsmart/config/IdentityGroupServices.java)
provides auth()/groups() and is implemented by AppContainer with real dependencies.
FeatureServices extends this subset and retains the other four feature accessors;
AppContainer wires real expense/balance services; settlements are also wired;
history is wired to DefaultHistoryService, which projects the coherent ledger in Java.
FeatureFactory supplies SavedStateHandle and constructor-injected services to ViewModels.
Screens never fetch Firebase clients. Pending mutation IDs and exact payloads survive
configuration changes/process recreation through SavedStateHandle. Password fields are not saved.

After an acknowledged mutation, a child sets RESULT_OK with the immutable requestId,
groupId and new groupRevision and finishes. Parent ViewModels verify the current
session/scope, then request FRESH through their service. On reopen/resume or manual
refresh they also request FRESH, covering changes from other clients. Cancellation
or unknown outcome sends no successful result; retry the same request first.
The result is an invalidation hint, not authoritative balance data. Group deletion
acceptance returns to the group list and refreshes it. Identity/group Activities
implement these routes. Group creation/deletion return results; detail changes remain
visible in place and set the same result metadata. The parent reloads on resume/results.
Back navigation waits for pending mutations to resolve.


## Implemented financial updates - November 4, 2026

The existing v1 settlement CRUD payloads now have implementations. Financial
writers share coherent transaction reads, exact validation and receipts. No new
wire models or actor-supplied identity fields are introduced. Firestore monetary
writes now use safe integers; readers preserve compatibility with earlier
canonical string storage. Correct percentage allocation uses largest fractional
remainder and UID tie-break in both Java and TypeScript.

The additive internal `GroupUpdatesService` / `GroupUpdatesRepository` stream
provides revision invalidations through `GroupRevisionObserver`. Its returned
`GroupObservation` supports cancellation and a current-UID/epoch guard. The stream
contains no financial amounts; consumers reload the complete server ledger and
coalesce changes arriving during a read. The balance overview derives totals,
expense rows and settlement rows from one snapshot. Listeners stop when the page
pauses and restart with fresh reads after resume. Final publication checks session
scope so delayed worker results cannot cross accounts. Existing navigation keys
and Firestore rules remain unchanged.

Historical incorrectly rounded percentage expenses are explicitly rejected as
invalid ledger data, rather than silently changing existing obligations. Before
any deployment containing such data, review and reconcile those records. No such
deployment or migration was performed in this local feature work.
