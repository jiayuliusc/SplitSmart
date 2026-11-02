# Shared schema — contract baseline v1

This is the canonical v1 coordination contract. The Java records and TypeScript
shapes exist; persistence, validation, financial algorithms and product handlers
do not. All product Firestore rules remain deny-by-default. Private implementation
choices may not change the public representations below without a reviewed version
change. See [CONTRACTS.md](CONTRACTS.md) for calls and the transaction protocol.

## Java values

Records live in [`domain/model`](../app/src/main/java/edu/usc/splitsmart/domain/model).
The following component order is also the public constructor order. Record
accessors use component names, for example `expense.creatorId()`. `?` means nullable;
all other reference values are required. Primitive values cannot be null.
Constructors check required references and defensively copy collections; they do
not perform product validation, monetary calculations, sorting or normalization.
Collections and their elements are immutable values. Lists retain supplied order;
maps retain insertion order. Lists/maps reject null elements, keys and values.

| Record | Constructor components (in order) |
| --- | --- |
| [`User`](../app/src/main/java/edu/usc/splitsmart/domain/model/User.java) | `String uid`, `String name`, `String email`, `String uscId`, `Instant createdAt`, `Instant updatedAt` |
| [`MemberProfile`](../app/src/main/java/edu/usc/splitsmart/domain/model/MemberProfile.java) | `String uid`, `String name`, `String email` |
| [`Group`](../app/src/main/java/edu/usc/splitsmart/domain/model/Group.java) | `String groupId`, `String name`, `String description?`, `String ownerId`, `List<String> memberIds`, `long revision`, `GroupStatus status`, `Instant createdAt`, `Instant updatedAt` |
| [`ExpenseShare`](../app/src/main/java/edu/usc/splitsmart/domain/model/ExpenseShare.java) | `String participantId`, `long amountCents`, `Integer basisPoints?` |
| [`SplitInput`](../app/src/main/java/edu/usc/splitsmart/domain/model/SplitInput.java) | `String participantId`, `Long amountCents?`, `Integer basisPoints?` |
| [`ExpenseDraft`](../app/src/main/java/edu/usc/splitsmart/domain/model/ExpenseDraft.java) | `String title`, `long amountCents`, `String payerId`, `List<String> participantIds`, `SplitMethod splitMethod`, `Category category`, `String note?`, `List<SplitInput> splitInputs` |
| [`Expense`](../app/src/main/java/edu/usc/splitsmart/domain/model/Expense.java) | `String expenseId`, `String groupId`, `String title`, `long amountCents`, `String currency`, `String creatorId`, `String payerId`, `List<String> participantIds`, `SplitMethod splitMethod`, `Category category`, `String note?`, `LocalDate localDate`, `Instant createdAt`, `Instant updatedAt`, `List<ExpenseShare> shares` |
| [`SettlementDraft`](../app/src/main/java/edu/usc/splitsmart/domain/model/SettlementDraft.java) | `String payeeId`, `long amountCents`, `String note?` |
| [`Settlement`](../app/src/main/java/edu/usc/splitsmart/domain/model/Settlement.java) | `String settlementId`, `String groupId`, `String creatorId`, `String payerId`, `String payeeId`, `long amountCents`, `String currency`, `String note?`, `LocalDate localDate`, `Instant createdAt`, `Instant updatedAt` |
| [`HistoryItem`](../app/src/main/java/edu/usc/splitsmart/domain/model/HistoryItem.java) | `String recordId`, `String groupId`, `TransactionType type`, `String title`, `long amountCents`, `String currency`, `String creatorId`, `String payerId`, `String payeeId?`, `List<String> involvedMemberIds`, `Category category?`, `String note?`, `LocalDate localDate`, `Instant createdAt`, `Instant updatedAt` |
| [`HistoryFilter`](../app/src/main/java/edu/usc/splitsmart/domain/model/HistoryFilter.java) | `String keyword`, `TransactionType type?`, `Category category?`, `String memberId?`, `HistorySort sort` |
| [`PairBalance`](../app/src/main/java/edu/usc/splitsmart/domain/model/PairBalance.java) | `String otherUserId`, `long netCents` |
| [`BalanceSummary`](../app/src/main/java/edu/usc/splitsmart/domain/model/BalanceSummary.java) | `String groupId`, `String viewerId`, `long revision`, `List<PairBalance> pairs`, `long totalToPayCents`, `long totalToReceiveCents` |
| [`MonthlySummary`](../app/src/main/java/edu/usc/splitsmart/domain/model/MonthlySummary.java) | `String groupId`, `YearMonth month`, `long revision`, `int expenseCount`, `long totalCents`, `Map<Category, Long> categoryTotalsCents` |
| [`LedgerSnapshot`](../app/src/main/java/edu/usc/splitsmart/domain/model/LedgerSnapshot.java) | `String groupId`, `long revision`, `List<String> memberIds`, `List<Expense> expenses`, `List<Settlement> settlements` |
| [`ProfileDraft`](../app/src/main/java/edu/usc/splitsmart/domain/model/ProfileDraft.java) | `String name`, `String uscId` |
| [`GroupDraft`](../app/src/main/java/edu/usc/splitsmart/domain/model/GroupDraft.java) | `String name`, `String description?` |
| [`Session`](../app/src/main/java/edu/usc/splitsmart/domain/model/Session.java) | `String uid?`, `String email?` |
| [`DeletedRecord`](../app/src/main/java/edu/usc/splitsmart/domain/model/DeletedRecord.java) | `String recordId` |
| [`GroupDeletion`](../app/src/main/java/edu/usc/splitsmart/domain/model/GroupDeletion.java) | `String groupId`, `GroupStatus status` |

`Session(null, null)` is signed out; otherwise both UID and email are present.
Session identifies an Auth account, not profile readiness. A fresh missing profile
is `NOT_FOUND` and routes to registration completion, never ordinary group screens.
`User` is private; `MemberProfile` intentionally has no USC ID or private timestamps.
Never serialize a private User into a group member response. Passwords are method
arguments only, never fields of a shared stored model or callable request.

Enums are exact, case-sensitive names:

- `Category`: FOOD, HOUSING, TRANSPORTATION, ENTERTAINMENT, OTHER.
- `SplitMethod`: EQUAL, EXACT, PERCENTAGE.
- `TransactionType`: EXPENSE, SETTLEMENT.
- `GroupStatus`: ACTIVE, DELETING.
- `HistorySort`: NEWEST_FIRST, OLDEST_FIRST.

## Representation rules

Money uses Java `long` cents. The single supported magnitude is
**9,007,199,254,740,991 cents** (`ContractVersion.MAX_ABSOLUTE_CENTS`, 2^53−1).
Stored expense/settlement totals are 1..MAX, shares and summary totals are 0..MAX,
and signed pair balances are −MAX..MAX. Callable money is a **canonical decimal
string**: `"1000"`, never `10.00`, a JSON number, `"01000"`, `"+1000"` or `"-0"`.
Only signed result fields may contain a minus sign. USD is the only currency and
`currency` is exactly `"USD"`. Invalid syntax/range is rejected, not rounded.

Firestore money is an integer. The server must parse wire strings as TypeScript
`bigint`, do all intermediate monetary multiplication/accumulation in `bigint`,
and check the supported range before converting to `number` for integer storage.
Java calculators must use `BigInteger` for intermediate products/aggregates and
check the same range before narrowing to `long`. This is a required future
implementation policy, not an implemented calculator. Overflow yields
`OUT_OF_RANGE`; never wrap, clamp, partially save, or show an inaccurate summary.
A large dataset is not silently truncated to keep totals in range.

Percentages are integer basis points (0..10000), serialized as JSON **numbers**;
100.00% is 10000. Group revisions are Java `long`, start at **1**, and are 1..MAX
in persisted groups. Expected/response revisions use canonical nonnegative decimal
strings on the wire and integers in Firestore. An exhausted revision counter fails
`OUT_OF_RANGE` without a mutation. Zero is representable for initial local state,
but cannot match a persisted group's revision.

All IDs are opaque ASCII strings `[A-Za-z0-9_-]{1,128}`. Firebase-generated UIDs and
Firestore auto IDs fit this baseline. Synthetic fixture IDs need not have the
length of real generated IDs. IDs are not names or email addresses. New document
IDs are generated by the backend; clients submit an existing ID only for a scoped
read/update/delete. All child domain objects include `groupId`; their repository
adapter obtains both IDs from the document path. Request IDs are a separate
lowercase UUID v4 format, described in CONTRACTS.md.

Java dates use `LocalDate` and `YearMonth`; wire/store values are Gregorian
`yyyy-MM-dd` and `yyyy-MM` (years 0001..9999). Never use the Java week-year `YYYY`.
At a new expense/settlement action, the ViewModel captures the device's local date
automatically once and retains it with the request ID. It is passed explicitly to
the service/repository create method. This prevents midnight retries from changing
the payload. Update/delete requests have no date field; the original is immutable.

Domain timestamps are `Instant`; Firestore uses `Timestamp`. The SDK-free wire
shape is **`{ "seconds": 1793664000, "nanoseconds": 123456000 }`**. Seconds are an
integer JSON number in −62135596800..253402300799. Nanoseconds are an integer in
0..999999999 and a multiple of 1000: persisted Firestore timestamps have microsecond
precision. Repositories preserve both components, not a millisecond-only `Date`.
Backend-created timestamps are server-clock values at microsecond precision; they
are not claimed to be the database commit timestamp. `createdAt` and `localDate`
never change on edit; `updatedAt` does. See the [Firestore type specification](https://firebase.google.com/docs/firestore/manage-data/data-types).

Optional Java nulls become **omitted JSON properties**; explicit JSON `null` is
invalid in v1. An omitted optional field in a full replacement draft clears that
field. Empty note/description strings are allowed and remain distinct from omitted.
There are no PATCH-style partial drafts. Unknown fields and unsupported versions
must be rejected by future runtime decoders. Type definitions alone do not validate
untrusted JSON; the baseline's parser checks exist only in compatibility tests.

## Stored documents

All product documents below have integer `schemaVersion: 1`. Paths are authoritative;
IDs shown in domain/wire results are not duplicated as stored fields unless listed.
Timestamps in this table mean Firestore Timestamp, money/revision mean integer.
Optional stored fields are absent, never null.

| Path | Stored fields in addition to schemaVersion | Access / lifecycle |
| --- | --- | --- |
| `users/{uid}` | `name`, normalized lowercase `email`, private 10-digit string `uscId`, `createdAt`, `updatedAt` | Future self-only read; backend writes. Leading zeros preserved. |
| `uscIdClaims/{uscId}` | `uid`, `createdAt` | Server-only unique reservation. Never expose IDs through client rules, URLs or logs. A simple private key avoids a second secret/hash scheme in v1. |
| `groups/{groupId}` | `name`, optional `description`, `ownerId`, UID-sorted unique `memberIds`, `revision`, `status`, `createdAt`, `updatedAt` | ACTIVE member reads later; backend writes. Owner is initial member. DELETING blocks new operations and ordinary reads. |
| `groups/{groupId}/expenses/{expenseId}` | `title`, `amountCents`, `currency`, `creatorId`, `payerId`, UID-sorted unique `participantIds`, `splitMethod`, `category`, optional `note`, `localDate`, timestamps, `shares` | Member reads later; only creator changes via backend. |
| Embedded expense `shares` | UID-sorted **array** of `{participantId, amountCents, basisPoints?}` | Atomic part of the expense, not a subcollection or UID-keyed map. `basisPoints` exists only for PERCENTAGE. |
| `groups/{groupId}/settlements/{settlementId}` | `creatorId`, `payerId`, `payeeId`, `amountCents`, `currency`, optional `note`, `localDate`, timestamps | Authenticated debtor supplies both creator/payer at creation; fields stored independently and immutable. |
| `mutationReceipts/{actorUid}/requests/{requestId}` | `operation`, `canonicalPayload`, `payloadHash`, `outcome` (SUCCESS/ERROR), `response?`, `error?`, `createdAt` | Server-only; exact original success or deterministic error. Retained without TTL, outside group deletion. Response and error are mutually exclusive. |
| `groupDeletionJobs/{groupId}` | `ownerId`, `status` (DELETING/DELETED), `createdAt`, `updatedAt` | Server-only resumable cleanup marker/tombstone. Parent ID is never reused. |
| `_diagnostics/connectivity` | Existing `message`, `projectId`, `schemaVersion` | Existing synthetic emulator-only exact get; no client write/list, no cloud access. |

This finalizes the old draft's share map as an ordered array and moves request
receipts outside the group so group deletion cannot erase retry protection.
`HistoryItem`, `HistoryFilter`, `PairBalance`, `BalanceSummary`, `MonthlySummary`,
`LedgerSnapshot`, drafts, session and callback results are not collections.
No independent editable history, summary, balance or public user directory exists.

## Drafts, projections and ordering

`ExpenseDraft` has no stored ID, groupId, creatorId, currency, timestamp, date or
computed shares. Scope/date are separate create arguments, currency is USD, and
actor comes from authentication. `splitInputs` is empty for EQUAL; EXACT has one
`{participantId, amountCents}` per participant; PERCENTAGE has one
`{participantId, basisPoints}` per participant. The unused input property is
omitted. Order matches UID-sorted participants. Future validators enforce at least
two distinct group-member participants including the payer, exact totals and
required category. Drafts must never be serialized as persisted Expense records.

`SettlementDraft` has only payee, amount and optional note. It cannot claim a
payer/creator. On edit, the backend retains both IDs and validates the replacement
against the ledger with the original settlement removed. `GroupDraft` has only
name/description; owner and initial membership come from authentication.
`ProfileDraft` has name/USC ID; normalized email comes from the authenticated
email/password account. Format eligibility does not prove USC identity.

Groups sort by groupId ascending; member profiles/IDs, participants, split inputs,
shares and pair balances sort by UID ascending using ordinary ASCII order. Ledger
expenses and settlements each sort by `(createdAt, record ID)` ascending. History
sorts by `(createdAt, type.name(), recordId)` ascending for OLDEST_FIRST; NEWEST_FIRST
reverses the whole ordering. Type is included because IDs can collide across the
two source collections. Returned lists are complete, immutable snapshots, never
partial pages disguised as complete ledgers.

`HistoryItem.title` is the expense title or literal `"Settlement"`. Its category
is absent for settlements; payeeId is absent for expenses. Involved IDs are expense
participants (including payer), or the settlement payer/payee, sorted and unique.
The keyword is an empty string for no search; nonempty search matches title/note
case-insensitively using `Locale.ROOT`. Active type/category/member filters combine
with AND; a category filter excludes settlements. No filter does not mean null
HistoryFilter: supply an explicit empty filter with a sort value.

`PairBalance.netCents` is positive when the other user owes the viewer and negative
when the viewer owes them. `BalanceSummary` carries the source group revision and
includes every other member, including zero balances. `MonthlySummary` contains
all five category map keys, including zero values; expense count and totals exclude
settlements. The month uses immutable localDate, not a timezone conversion of
createdAt. Constructors do not compute these projections.
