package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: basisPoints. Collections are defensive, unmodifiable copies. */
public record ExpenseShare(
        String participantId,
        long amountCents,
        Integer basisPoints) {
    public ExpenseShare {
        Objects.requireNonNull(participantId, "participantId");
    }
}
