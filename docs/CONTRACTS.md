# Draft interfaces and callable contracts

**Coordination reference only. No CRUD repository, service, calculator or product
handler is implemented.** The sketches below need review by affected owners before
becoming Java or TypeScript files. Firebase SDK objects must not cross these APIs.

## Plain Java async API proposal

```java
interface Callback<T> {
    void onSuccess(T result);
    void onError(AppError error);
}

interface UserRepository {
    void getCurrentProfile(Callback<User> callback);
    void completeProfile(ProfileDraft draft, String requestId, Callback<User> callback);
    void updateName(String name, String requestId, Callback<User> callback);
}

interface GroupRepository {
    void getGroupsForCurrentUser(Callback<List<Group>> callback);
    void getGroup(String groupId, Callback<Group> callback);
    void getMemberProfiles(String groupId, Callback<List<MemberProfile>> callback);
    void createGroup(GroupDraft draft, String requestId, Callback<Group> callback);
    void addMember(String groupId, String normalizedEmail, String requestId, Callback<Void> callback);
    void updateGroup(String groupId, GroupDraft draft, String requestId, Callback<Void> callback);
    void deleteGroup(String groupId, String requestId, Callback<Void> callback);
}

interface ExpenseRepository {
    void getExpensesForGroup(String groupId, Callback<List<Expense>> callback);
    void createExpense(String groupId, ExpenseDraft draft, String requestId, Callback<Expense> callback);
    void updateExpense(String groupId, String expenseId, ExpenseDraft draft,
                       String requestId, Callback<Expense> callback);
    void deleteExpense(String groupId, String expenseId, String requestId, Callback<Void> callback);
}

interface SettlementRepository {
    void getSettlementsForGroup(String groupId, Callback<List<Settlement>> callback);
    void createSettlement(String groupId, SettlementDraft draft,
                          String requestId, Callback<Settlement> callback);
    void updateSettlement(String groupId, String settlementId, SettlementDraft draft,
                          String requestId, Callback<Settlement> callback);
    void deleteSettlement(String groupId, String settlementId, String requestId, Callback<Void> callback);
}
```

Identity should additionally agree on an `AuthRepository` for sign-up, sign-in,
sign-out and session observation; it wraps Firebase Auth without exposing its
`FirebaseUser`. Registering an Auth identity and completing a private profile are
different operations. A missing profile must be recoverable without exposing
ordinary group data. The foundation does neither.

Reads return typed data or an explicit failure, not null-as-error. Decide whether
reads require a fresh server snapshot or may return labeled cache data. Callback
lifecycle/cancellation must be agreed before adding long-lived listeners. See
`ARCHITECTURE.md` for proposed error codes and callback thread policy.

Services own validation/previews and call these interfaces. Future calculator
contracts may use `previewSplit(ExpenseDraft)` and
`calculateBalances(currentUid, expenses, settlements)` returning immutable plain
values. Balance and Settlement services share the calculator, without a service
dependency cycle. History consumes Expense and Settlement reads and computes
projections; it does not persist a separate history. No algorithms are supplied.

## Callable proposal

All names and payloads below are draft coordination points. Use authenticated
callables in the agreed region (`us-central1` initially). The server derives
`creatorId`/actor from auth context, never trusts a client-supplied actor, and
authorizes every requested group. Auth sign-up/sign-in use the Auth SDK; profile
completion and all Firestore mutations use future trusted functions.

| Proposed callable family | Owner | Review focus |
| --- | --- | --- |
| `completeProfile`, `updateProfileName` | A | Eligible normalized email, private 10-digit ID, unique reservation, retry/recovery |
| `createGroup`, `addGroupMember`, `updateGroup`, `deleteGroup`, `getGroupMemberProfiles` | A | Ownership, registered membership, private data projection, cascade lifecycle |
| `createExpense`, `updateExpense`, `deleteExpense` | B with C/D | Creator vs payer, full allocation validation, revision, atomic replacement |
| `createSettlement`, `updateSettlement`, `deleteSettlement` | D with B/C | Debtor identity, authoritative pairwise debt, original exclusion on edit, concurrency |

Mutations should carry a stable client-generated `requestId`, scope IDs, schema
version and the draft payload. Monetary wire values are proposed decimal strings
of cents; percentage values are integers. Responses return identifiers and agreed
domain data, not raw private documents. Define consistent error codes including
conflict/retry behavior. Request replay with different content must fail rather
than silently return a previous result. Auth/session handling never substitutes
for backend authorization.

Before implementing: agree exact field names, numeric bounds, dates/timezones,
request retention, callback cancellation and read freshness; then update this
document and the schema in one reviewed change. B/C/D must also agree the common
revision and source-read transaction strategy and test concurrent mutation cases.
