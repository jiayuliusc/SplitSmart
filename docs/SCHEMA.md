# Draft Firestore schema — coordination reference

**Proposal for teammate agreement, not an implemented persistence contract.**
There are no product collections created by the foundation. Rules currently deny
all product reads and writes. Review field names, limits, indexes and API payloads
across A/B/C/D/E before creating models or migrations. All records should carry a
schema version once the first persistence contract is accepted.

| Path | Proposed fields | Intended access / ownership |
| --- | --- | --- |
| `users/{uid}` | `name`, normalized `email`, `uscId` (string), `createdAt`, `updatedAt` | Future self-only profile reads. Backend mutation only. No shared USC ID output. |
| `uscIdClaims/{claimKey}` | `uid`, `createdAt` | Server-only unique identity reservation. Agree on a nonpublic key strategy; never expose the directory to clients. |
| `groups/{groupId}` | `name`, optional `description`, `ownerId`, `memberIds` (UID array for the initial small-group design), `revision` (integer), `status` (ACTIVE/DELETING), timestamps | Future member reads, owner-only management via functions. Creator starts as owner/member. |
| `groups/{groupId}/expenses/{expenseId}` | `title`, `amountCents` (Firestore integer), `currency` = USD, `creatorId`, `payerId`, `participantIds`, `splitMethod`, `category`, optional `note`, immutable `localDate`, timestamps, `shares` | Future member reads, creator-only changes. Shares replace atomically with the expense. |
| Embedded `shares` keyed by participant UID | `amountCents` (integer), `basisPoints` (integer for percentage splits; otherwise absent) | Owned by the expense. No independently writable allocations. |
| `groups/{groupId}/settlements/{settlementId}` | `creatorId`, `payerId`, `payeeId`, `amountCents`, `currency` = USD, optional `note`, immutable `localDate`, timestamps | Future member reads, creator-only changes via authoritative functions. Records offline payments only. |
| `groups/{groupId}/mutationRequests/{requestId}` | `actorId`, `operation`, payload fingerprint, result reference, timestamps | Proposed server-only idempotency records scoped to actor/operation. Define retention and retry semantics before implementation. |
| `_diagnostics/connectivity` | `message`, `projectId`, `schemaVersion` | **Only existing fixture:** exact unauthenticated get in emulator rules, no listing or client writes. No access in cloud rules. |

`MemberProfile(uid, name, email)` is an authorized backend projection for relevant
group members, not a public user collection. Private profiles must not be reused
for group-list serialization. Email lookup needs scoped authorization and bounded
responses; the proposed design does not permit a complete directory download.

`Group`, `User`, `Expense`, `ExpenseShare` and `Settlement` remain plain Java
domain types. Add `MemberProfile`, `ExpenseDraft`, `PairBalance`, `BalanceSummary`,
`HistoryItem`, `HistoryFilter` and `MonthlySummary` only as their consumers arrive.
No independent editable balance, history or summary collection is proposed.

## Authoritative mutations and concurrency

Retain the catalog's callable boundary. Every future mutation must authenticate
the actor, authorize membership/ownership/creator, validate its input against
authoritative state, and preserve immutable metadata. An expense payer may differ
from its creator. Payer must be a member and one of at least two participants.

The financial design proposal is to read and update a **common group revision**
inside the same Firestore transaction as each financial source mutation, so
competing writers conflict and retry. Expense create/edit/delete and settlement
create/edit/delete must all participate. A revision increment alone is not enough:
the transaction must read the authoritative source snapshot used for validation.
Define a bounded snapshot strategy and test concurrent writers before enabling
financial endpoints. Membership/deletion changes must use compatible guards.

Settlement replacement validates debt with the old record excluded. An original
expense correction may produce a signed credit; preserve historical settlements.
Functions need durable idempotency for client retries and resumable group deletion;
deleting a parent document does not delete its Firestore subcollections. Use a
deleting state to block new child writes during cleanup. These mechanisms are not
implemented by this foundation.

## Numeric and date mapping

Money is Java `long` cents and Firestore integer, with a team-agreed range that
also makes server computation exact. Callable payloads propose decimal strings
for cents; validate them and overflow/aggregate limits before converting. The team
must choose bounded safe integers or a BigInt-backed implementation before
financial handlers. Do not rely on rounding a JavaScript floating-point result.

Percentages are integer basis points; exact and percentage inputs must include
every selected participant. See `ARCHITECTURE.md` for naming and date conventions.
Keep local calendar date immutable for month bucketing and use authoritative
`createdAt` plus stable document ID for same-day sorting. `updatedAt` changes on
edit. SDK timestamp types stay inside adapters.

## Queries and indexes

Initial proposal: list groups with `memberIds array-contains currentUid`, then read
authorized group expenses/settlements. Search/filter/summary work can operate on
the specified small dataset. Add composite indexes only when actual queries
require them; `firestore.indexes.json` is intentionally empty. Rules must enforce
authorization for queries as well as single-document gets. The emulator does not
prove production index availability or cloud performance.
