package edu.usc.splitsmart.data.contract;

import java.util.Objects;

/** Exact v1 request shapes; actor identity is never a caller-supplied field. */
public final class CallableRequests {
    private CallableRequests() {}

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record CompleteProfileRequest(
            int schemaVersion,
            String requestId,
            WireModels.ProfileDraft profile) {
        public CompleteProfileRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(profile, "profile");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record UpdateProfileNameRequest(
            int schemaVersion,
            String requestId,
            String name) {
        public UpdateProfileNameRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(name, "name");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record CreateGroupRequest(
            int schemaVersion,
            String requestId,
            WireModels.GroupDraft draft) {
        public CreateGroupRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record AddGroupMemberRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision,
            String email) {
        public AddGroupMemberRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            Objects.requireNonNull(email, "email");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record UpdateGroupRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision,
            WireModels.GroupDraft draft) {
        public UpdateGroupRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record DeleteGroupRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision) {
        public DeleteGroupRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record GetGroupMemberProfilesRequest(
            int schemaVersion,
            String groupId) {
        public GetGroupMemberProfilesRequest {
            Objects.requireNonNull(groupId, "groupId");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record GetGroupLedgerRequest(
            int schemaVersion,
            String groupId) {
        public GetGroupLedgerRequest {
            Objects.requireNonNull(groupId, "groupId");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record CreateExpenseRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision,
            String localDate,
            WireModels.ExpenseDraft draft) {
        public CreateExpenseRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            Objects.requireNonNull(localDate, "localDate");
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record UpdateExpenseRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision,
            String expenseId,
            WireModels.ExpenseDraft draft) {
        public UpdateExpenseRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            Objects.requireNonNull(expenseId, "expenseId");
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record DeleteExpenseRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision,
            String expenseId) {
        public DeleteExpenseRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            Objects.requireNonNull(expenseId, "expenseId");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record CreateSettlementRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision,
            String localDate,
            WireModels.SettlementDraft draft) {
        public CreateSettlementRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            Objects.requireNonNull(localDate, "localDate");
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record UpdateSettlementRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision,
            String settlementId,
            WireModels.SettlementDraft draft) {
        public UpdateSettlementRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            Objects.requireNonNull(settlementId, "settlementId");
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record DeleteSettlementRequest(
            int schemaVersion,
            String requestId,
            String groupId,
            String expectedRevision,
            String settlementId) {
        public DeleteSettlementRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            Objects.requireNonNull(settlementId, "settlementId");
        }
    }
}
