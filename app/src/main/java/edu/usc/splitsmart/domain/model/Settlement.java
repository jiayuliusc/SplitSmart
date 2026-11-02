package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import java.time.Instant;
import java.time.LocalDate;

/** Contract v1. Nullable components: note. Collections are defensive, unmodifiable copies. */
public record Settlement(
        String settlementId,
        String groupId,
        String creatorId,
        String payerId,
        String payeeId,
        long amountCents,
        String currency,
        String note,
        LocalDate localDate,
        Instant createdAt,
        Instant updatedAt) {
    public Settlement {
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(payerId, "payerId");
        Objects.requireNonNull(payeeId, "payeeId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(localDate, "localDate");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
