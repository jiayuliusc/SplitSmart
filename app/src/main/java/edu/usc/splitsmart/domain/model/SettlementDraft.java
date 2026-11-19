package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: payerId (absent = acting user), note.
 * Collections are defensive, unmodifiable copies. */
public record SettlementDraft(
        String payerId,
        String payeeId,
        long amountCents,
        String note) {
    public SettlementDraft {
        Objects.requireNonNull(payeeId, "payeeId");
    }
}
