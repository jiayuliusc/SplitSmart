/** Contract baseline v1. Definitions only: no SDK calls, serializers, or product handlers. */
export const SCHEMA_VERSION = 1 as const;
export const MAX_ABSOLUTE_CENTS = 9007199254740991n;
export const MAX_REVISION = 9007199254740991n;
export const TOTAL_BASIS_POINTS = 10000;
export const CURRENCY = "USD" as const;

// Canonical decimal strings; range, sign, and format must also be checked at runtime.
export type Cents = `${bigint}`;
export type Revision = `${bigint}`;
// yyyy-MM-dd, Gregorian calendar; runtime decoder must reject invalid dates.
export type LocalDate = string;
export type Category = "FOOD" | "HOUSING" | "TRANSPORTATION" | "ENTERTAINMENT" | "OTHER";
export type SplitMethod = "EQUAL" | "EXACT" | "PERCENTAGE";
export type TransactionType = "EXPENSE" | "SETTLEMENT";
export type GroupStatus = "ACTIVE" | "DELETING";
export type HistorySort = "NEWEST_FIRST" | "OLDEST_FIRST";
export type ErrorCode = "VALIDATION" | "UNAUTHENTICATED" | "PERMISSION_DENIED" | "NOT_FOUND" | "CACHE_MISS" | "ALREADY_EXISTS" | "CONFLICT" | "IDEMPOTENCY_MISMATCH" | "GROUP_DELETING" | "OUT_OF_RANGE" | "RESOURCE_EXHAUSTED" | "UNAVAILABLE" | "DEADLINE_EXCEEDED" | "INTERNAL";

export interface User {
  readonly uid: string;
  readonly name: string;
  readonly email: string;
  readonly uscId: string;
  readonly createdAt: Timestamp;
  readonly updatedAt: Timestamp;
}

export interface MemberProfile {
  readonly uid: string;
  readonly name: string;
  readonly email: string;
}

export interface Group {
  readonly groupId: string;
  readonly name: string;
  readonly description?: string;
  readonly ownerId: string;
  readonly memberIds: ReadonlyArray<string>;
  readonly revision: Revision;
  readonly status: GroupStatus;
  readonly createdAt: Timestamp;
  readonly updatedAt: Timestamp;
}

export interface ExpenseShare {
  readonly participantId: string;
  readonly amountCents: Cents;
  readonly basisPoints?: number;
}

export interface SplitInput {
  readonly participantId: string;
  readonly amountCents?: Cents;
  readonly basisPoints?: number;
}

export interface ExpenseDraft {
  readonly title: string;
  readonly amountCents: Cents;
  readonly payerId: string;
  readonly participantIds: ReadonlyArray<string>;
  readonly splitMethod: SplitMethod;
  readonly category: Category;
  readonly note?: string;
  readonly splitInputs: ReadonlyArray<SplitInput>;
}

export interface Expense {
  readonly expenseId: string;
  readonly groupId: string;
  readonly title: string;
  readonly amountCents: Cents;
  readonly currency: typeof CURRENCY;
  readonly creatorId: string;
  readonly payerId: string;
  readonly participantIds: ReadonlyArray<string>;
  readonly splitMethod: SplitMethod;
  readonly category: Category;
  readonly note?: string;
  readonly localDate: LocalDate;
  readonly createdAt: Timestamp;
  readonly updatedAt: Timestamp;
  readonly shares: ReadonlyArray<ExpenseShare>;
}

export interface SettlementDraft {
  readonly payeeId: string;
  readonly amountCents: Cents;
  readonly note?: string;
}

export interface Settlement {
  readonly settlementId: string;
  readonly groupId: string;
  readonly creatorId: string;
  readonly payerId: string;
  readonly payeeId: string;
  readonly amountCents: Cents;
  readonly currency: typeof CURRENCY;
  readonly note?: string;
  readonly localDate: LocalDate;
  readonly createdAt: Timestamp;
  readonly updatedAt: Timestamp;
}

export interface LedgerSnapshot {
  readonly groupId: string;
  readonly revision: Revision;
  readonly memberIds: ReadonlyArray<string>;
  readonly expenses: ReadonlyArray<Expense>;
  readonly settlements: ReadonlyArray<Settlement>;
}

export interface ProfileDraft {
  readonly name: string;
  readonly uscId: string;
}

export interface GroupDraft {
  readonly name: string;
  readonly description?: string;
}

export interface DeletedRecord {
  readonly recordId: string;
}

export interface GroupDeletion {
  readonly groupId: string;
  readonly status: GroupStatus;
}

export interface Timestamp {
  readonly seconds: number;
  readonly nanoseconds: number;
}

export interface GroupMutationResponse<T> {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupRevision: Revision;
  readonly value: T;
}

export interface ProfileMutationResponse {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly value: User;
}

export interface MembersResponse {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly members: ReadonlyArray<MemberProfile>;
}

export interface LedgerResponse {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly ledger: LedgerSnapshot;
}

export interface ErrorDetails {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly code: ErrorCode;
  readonly field?: string;
  readonly retryable: boolean;
}

export interface CompleteProfileRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly profile: ProfileDraft;
}

export interface UpdateProfileNameRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly name: string;
}

export interface CreateGroupRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly draft: GroupDraft;
}

export interface AddGroupMemberRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
  readonly email: string;
}

export interface UpdateGroupRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
  readonly draft: GroupDraft;
}

export interface DeleteGroupRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
}

export interface GetGroupMemberProfilesRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly groupId: string;
}

export interface GetGroupLedgerRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly groupId: string;
}

export interface CreateExpenseRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
  readonly localDate: LocalDate;
  readonly draft: ExpenseDraft;
}

export interface UpdateExpenseRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
  readonly expenseId: string;
  readonly draft: ExpenseDraft;
}

export interface DeleteExpenseRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
  readonly expenseId: string;
}

export interface CreateSettlementRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
  readonly localDate: LocalDate;
  readonly draft: SettlementDraft;
}

export interface UpdateSettlementRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
  readonly settlementId: string;
  readonly draft: SettlementDraft;
}

export interface DeleteSettlementRequest {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestId: string;
  readonly groupId: string;
  readonly expectedRevision: Revision;
  readonly settlementId: string;
}

export const CALLABLE_OWNERS = {
  completeProfile: "A",
  updateProfileName: "A",
  createGroup: "A",
  addGroupMember: "A",
  updateGroup: "A",
  deleteGroup: "A",
  getGroupMemberProfiles: "A",
  getGroupLedger: "C",
  createExpense: "B",
  updateExpense: "B",
  deleteExpense: "B",
  createSettlement: "D",
  updateSettlement: "D",
  deleteSettlement: "D",
} as const;

export interface Requests {
  completeProfile: CompleteProfileRequest;
  updateProfileName: UpdateProfileNameRequest;
  createGroup: CreateGroupRequest;
  addGroupMember: AddGroupMemberRequest;
  updateGroup: UpdateGroupRequest;
  deleteGroup: DeleteGroupRequest;
  getGroupMemberProfiles: GetGroupMemberProfilesRequest;
  getGroupLedger: GetGroupLedgerRequest;
  createExpense: CreateExpenseRequest;
  updateExpense: UpdateExpenseRequest;
  deleteExpense: DeleteExpenseRequest;
  createSettlement: CreateSettlementRequest;
  updateSettlement: UpdateSettlementRequest;
  deleteSettlement: DeleteSettlementRequest;
}

export interface Responses {
  completeProfile: ProfileMutationResponse;
  updateProfileName: ProfileMutationResponse;
  createGroup: GroupMutationResponse<Group>;
  addGroupMember: GroupMutationResponse<Group>;
  updateGroup: GroupMutationResponse<Group>;
  deleteGroup: GroupMutationResponse<GroupDeletion>;
  getGroupMemberProfiles: MembersResponse;
  getGroupLedger: LedgerResponse;
  createExpense: GroupMutationResponse<Expense>;
  updateExpense: GroupMutationResponse<Expense>;
  deleteExpense: GroupMutationResponse<DeletedRecord>;
  createSettlement: GroupMutationResponse<Settlement>;
  updateSettlement: GroupMutationResponse<Settlement>;
  deleteSettlement: GroupMutationResponse<DeletedRecord>;
}

export type CallableName = keyof Requests;

/** Only record shapes used by the compatibility fixture; not additional callables. */
export interface PayloadTypes {
  ProfileMutationResponse: ProfileMutationResponse;
  User: User;
  MemberProfile: MemberProfile;
  Group: Group;
  ExpenseShare: ExpenseShare;
  SplitInput: SplitInput;
  ExpenseDraft: ExpenseDraft;
  Expense: Expense;
  SettlementDraft: SettlementDraft;
  Settlement: Settlement;
  LedgerSnapshot: LedgerSnapshot;
  ProfileDraft: ProfileDraft;
  GroupDraft: GroupDraft;
  DeletedRecord: DeletedRecord;
  GroupDeletion: GroupDeletion;
  Timestamp: Timestamp;
  MembersResponse: MembersResponse;
  LedgerResponse: LedgerResponse;
  ErrorDetails: ErrorDetails;
}
