package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import edu.usc.splitsmart.util.ImmutableCollections;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contract v1. Nullable components: note. Collections are defensive, unmodifiable copies. */
public record Expense(
        String expenseId,
        String groupId,
        String title,
        long amountCents,
        String currency,
        String creatorId,
        String payerId,
        List<String> participantIds,
        SplitMethod splitMethod,
        Category category,
        String note,
        LocalDate localDate,
        Instant createdAt,
        Instant updatedAt,
        List<ExpenseShare> shares) {
    public Expense {
        Objects.requireNonNull(expenseId, "expenseId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(title, "title");
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
