package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import edu.usc.splitsmart.util.ImmutableCollections;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contract v1. Nullable components: payeeId, category, note. Collections are defensive, unmodifiable copies. */
public record HistoryItem(
        String recordId,
        String groupId,
        TransactionType type,
        String title,
        long amountCents,
        String currency,
        String creatorId,
        String payerId,
        String payeeId,
        List<String> involvedMemberIds,
        Category category,
        String note,
        LocalDate localDate,
        Instant createdAt,
        Instant updatedAt) {
    public HistoryItem {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(payerId, "payerId");
        involvedMemberIds = ImmutableCollections.list(involvedMemberIds);
        Objects.requireNonNull(localDate, "localDate");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
