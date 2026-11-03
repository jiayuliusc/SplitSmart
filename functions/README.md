# Authoritative identity/group operations

`src/index.ts` exports completeProfile, updateProfileName, createGroup,
addGroupMember, updateGroup, deleteGroup, getGroupMemberProfiles, and the
retry-enabled cleanupDeletedGroup Firestore trigger. X01 uses Firebase Auth's
reset-email API directly and has no callable. No financial handler is exported.

`src/infrastructure` owns strict input checks, error mapping, canonical receipts
and shared record/authorization helpers. `src/identity/handlers.ts` owns the seven
identity/group calls. `src/groups/cleanup.ts` performs resumable bounded deletion.
Stored profiles, claims and receipts remain private; member responses contain only
UID/name/email. No email-verification check or email-verification sender exists.

Run `npm ci` from the root, `npm run check:contracts`, and `npm run check:emulators`.
The latter runs synthetic account/reset/group/rules/deletion tests on demo-splitsmart
only. The Auth emulator exposes reset actions locally and does not send real email.
Actual cloud email delivery, resources and deployments remain owner configuration.

V1 payloads are in `src/contracts/v1.ts`; contract constants are not function exports.
See [contracts](../docs/CONTRACTS.md), [schema](../docs/SCHEMA.md), and
[implementation map](../docs/USER_GROUP_IMPLEMENTATION.md). Keep future financial
writers on the documented group revision/receipt/ledger protocol. Do not introduce
parallel payload definitions or bypass the deletion marker.

Monitor retry failures in cloud deployments. Retained DELETING jobs can be resumed
by invoking the cleanup implementation from trusted administration if the event
retry window expires. Never expose the cleanup helper as an unauthenticated endpoint.
