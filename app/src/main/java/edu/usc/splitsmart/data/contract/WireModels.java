package edu.usc.splitsmart.data.contract;

import java.util.List;
import java.util.Objects;
import edu.usc.splitsmart.domain.model.Category;
import edu.usc.splitsmart.domain.model.SplitMethod;
import edu.usc.splitsmart.domain.model.GroupStatus;
import edu.usc.splitsmart.domain.contract.ErrorCode;
import edu.usc.splitsmart.util.ImmutableCollections;

/** Callable v1 JSON shapes. Null optional components are OMITTED on the wire. */
public final class WireModels {
    private WireModels() {}

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record User(
            String uid,
            String name,
            String email,
            String uscId,
            Timestamp createdAt,
            Timestamp updatedAt) {
        public User {
            Objects.requireNonNull(uid, "uid");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(uscId, "uscId");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }

        @Override
        public String toString() {
            return "User[private profile]";
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record MemberProfile(
            String uid,
            String name,
            String email) {
        public MemberProfile {
            Objects.requireNonNull(uid, "uid");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(email, "email");
        }
    }

    /** Contract v1. Nullable components: description. Collections are defensive, unmodifiable copies. */
    public record Group(
            String groupId,
            String name,
            String description,
            String ownerId,
            List<String> memberIds,
            String revision,
            GroupStatus status,
            Timestamp createdAt,
            Timestamp updatedAt) {
        public Group {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(ownerId, "ownerId");
            memberIds = ImmutableCollections.list(memberIds);
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    /** Contract v1. Nullable components: basisPoints. Collections are defensive, unmodifiable copies. */
    public record ExpenseShare(
            String participantId,
            String amountCents,
            Integer basisPoints) {
        public ExpenseShare {
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(amountCents, "amountCents");
        }
    }

    /** Contract v1. Nullable components: amountCents, basisPoints. Collections are defensive, unmodifiable copies. */
    public record SplitInput(
            String participantId,
            String amountCents,
            Integer basisPoints) {
        public SplitInput {
            Objects.requireNonNull(participantId, "participantId");
        }
    }

    /** Contract v1. Nullable components: note. Collections are defensive, unmodifiable copies. */
    public record ExpenseDraft(
            String title,
            String amountCents,
            String payerId,
            List<String> participantIds,
            SplitMethod splitMethod,
            Category category,
            String note,
            List<SplitInput> splitInputs) {
        public ExpenseDraft {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(amountCents, "amountCents");
            Objects.requireNonNull(payerId, "payerId");
            participantIds = ImmutableCollections.list(participantIds);
            Objects.requireNonNull(splitMethod, "splitMethod");
            if (category == null) {
                category = Category.OTHER;
            }
            splitInputs = ImmutableCollections.list(splitInputs);
        }
    }

    /** Contract v1. Nullable components: note. Collections are defensive, unmodifiable copies. */
    public record Expense(
            String expenseId,
            String groupId,
            String title,
            String amountCents,
            String currency,
            String creatorId,
            String payerId,
            List<String> participantIds,
            SplitMethod splitMethod,
            Category category,
            String note,
            String localDate,
            Timestamp createdAt,
            Timestamp updatedAt,
            List<ExpenseShare> shares) {
        public Expense {
            Objects.requireNonNull(expenseId, "expenseId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(amountCents, "amountCents");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(creatorId, "creatorId");
            Objects.requireNonNull(payerId, "payerId");
            participantIds = ImmutableCollections.list(participantIds);
            Objects.requireNonNull(splitMethod, "splitMethod");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(localDate, "localDate");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            shares = ImmutableCollections.list(shares);
        }
    }

    /** Contract v1. Nullable components: note. Collections are defensive, unmodifiable copies. */
    public record SettlementDraft(
            String payeeId,
            String amountCents,
            String note) {
        public SettlementDraft {
            Objects.requireNonNull(payeeId, "payeeId");
            Objects.requireNonNull(amountCents, "amountCents");
        }
    }

    /** Contract v1. Nullable components: note. Collections are defensive, unmodifiable copies. */
    public record Settlement(
            String settlementId,
            String groupId,
            String creatorId,
            String payerId,
            String payeeId,
            String amountCents,
            String currency,
            String note,
            String localDate,
            Timestamp createdAt,
            Timestamp updatedAt) {
        public Settlement {
            Objects.requireNonNull(settlementId, "settlementId");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(creatorId, "creatorId");
            Objects.requireNonNull(payerId, "payerId");
            Objects.requireNonNull(payeeId, "payeeId");
            Objects.requireNonNull(amountCents, "amountCents");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(localDate, "localDate");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record LedgerSnapshot(
            String groupId,
            String revision,
            List<String> memberIds,
            List<Expense> expenses,
            List<Settlement> settlements) {
        public LedgerSnapshot {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(revision, "revision");
            memberIds = ImmutableCollections.list(memberIds);
            expenses = ImmutableCollections.list(expenses);
            settlements = ImmutableCollections.list(settlements);
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record ProfileDraft(
            String name,
            String uscId) {
        public ProfileDraft {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(uscId, "uscId");
        }

        @Override
        public String toString() {
            return "ProfileDraft[private profile]";
        }
    }

    /** Contract v1. Nullable components: description. Collections are defensive, unmodifiable copies. */
    public record GroupDraft(
            String name,
            String description) {
        public GroupDraft {
            Objects.requireNonNull(name, "name");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record DeletedRecord(
            String recordId) {
        public DeletedRecord {
            Objects.requireNonNull(recordId, "recordId");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record GroupDeletion(
            String groupId,
            GroupStatus status) {
        public GroupDeletion {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(status, "status");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record Timestamp(
            long seconds,
            int nanoseconds) {
        public Timestamp {
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record GroupMutationResponse<T>(
            int schemaVersion,
            String requestId,
            String groupRevision,
            T value) {
        public GroupMutationResponse {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(groupRevision, "groupRevision");
            Objects.requireNonNull(value, "value");
        }
    }

    /** Profile mutations have no group revision field. */
    public record ProfileMutationResponse(int schemaVersion, String requestId, User value) {
        public ProfileMutationResponse {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(value, "value");
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record MembersResponse(
            int schemaVersion,
            List<MemberProfile> members) {
        public MembersResponse {
            members = ImmutableCollections.list(members);
        }
    }

    /** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
    public record LedgerResponse(
            int schemaVersion,
            LedgerSnapshot ledger) {
        public LedgerResponse {
            Objects.requireNonNull(ledger, "ledger");
        }
    }

    /** Contract v1. Nullable components: field. Collections are defensive, unmodifiable copies. */
    public record ErrorDetails(
            int schemaVersion,
            ErrorCode code,
            String field,
            boolean retryable) {
        public ErrorDetails {
            Objects.requireNonNull(code, "code");
        }
    }
}
