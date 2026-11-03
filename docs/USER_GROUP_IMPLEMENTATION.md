# User/group management and X01 implementation map

Scope: catalog section 1 and X01 only. Main starting point: `61ad650`.
X06 email verification and all other catalog features are excluded.

| Flow | Screen / ViewModel | Service / repository | Backend / rules | Verification |
| --- | --- | --- | --- | --- |
| Register, recover incomplete account | RegisterScreen, CompleteProfileScreen / AuthViewModel | DefaultAuthService / FirebaseAuthRepository, FirebaseUserRepository | completeProfile; atomic USC ID claim/profile/receipt | validation, duplicate/concurrent claims, Auth-success/profile-failure recovery |
| Login/logout | LoginScreen / AuthViewModel | DefaultAuthService / FirebaseAuthRepository | Firebase Auth; profile readiness gate | valid/invalid credentials, session change, cancelled/late results |
| X01 password reset | PasswordResetScreen / AuthViewModel | sendPasswordResetEmail via Auth repository | Firebase Auth email action; no callable | confirmation, invalid email, transport error, emulator reset link |
| View profile/update name | ProfileScreen / ProfileViewModel | DefaultAuthService / FirebaseUserRepository | self-only users read; updateProfileName | private fields, immutable email/ID, idempotency |
| List/create groups | GroupListScreen, CreateGroupScreen / GroupListViewModel | DefaultGroupService / FirebaseGroupRepository | member-only ACTIVE query; createGroup | multiple groups, owner/initial membership, retries |
| Details/members/owner edits/addition | GroupDetailScreen / GroupDetailViewModel | DefaultGroupService / FirebaseGroupRepository | updateGroup, addGroupMember, getGroupMemberProfiles | member versus owner, registered USC email, stale revisions, projection privacy |
| Delete group | GroupDetailScreen / GroupDetailViewModel | DefaultGroupService / FirebaseGroupRepository | deleteGroup + durable job/receipt + retryable cleanup trigger | DELETING exclusion, bounded resumable batches, duplicate events, denial and receipt replay |

## Necessary shared changes identified before implementation

- Add `AsyncOperation sendPasswordResetEmail(String email, Callback<Void> callback)`
  to AuthRepository and AuthService. Its queued null success acknowledges Firebase
  accepting the email request, not receipt of mail or existence of an account.
- Add `IdentityGroupServices` containing auth()/groups(); FeatureServices extends
  it. AppContainer implements only the available subset. No unavailable stubs.
- Stored schema, existing callable names/payloads and financial interfaces stay v1.
  Product writes stay denied. Enable only reviewed self-profile and active-member
  group reads; financial collections remain denied.
- Validation choices: trimmed nonblank names up to 100 UTF-16 code units; optional
  group description up to 1000; normalized lowercase exact usc.edu email up to
  254; ASCII 10-digit USC ID. Passwords are not trimmed or saved, and registration
  requires 6–4096 code units (Firebase enforces its configured policy as well).

## Implementation boundaries

Receipts are actor-scoped and replay exact success/error after canonical-payload
matching. Deterministic errors commit receipts before being surfaced. User/group
handlers independently authorize callers and never accept actors from payloads.
Deletion reads and deletes only existing expense/settlement documents; this does
not implement those features. Cleanup batches contain at most 200 child deletes,
re-read parent/job in each transaction and keep the deletion tombstone/receipts.

Screens obtain IDs from explicit Activity arguments, dependencies from injected
ViewModel factories, session from AuthService, and fresh data on resume/results.
Unknown mutation outcomes retain the same request ID/payload for retry; conflicts
require fresh data and a new intent. Password fields are never saved across process
death. Profile readiness is checked on startup/login, and registration success
enters the group list automatically. No email-verification gate is introduced.

See [VERIFICATION.md](VERIFICATION.md) for executed checks. PR review and final
GitHub check results are recorded in the feature PR.
