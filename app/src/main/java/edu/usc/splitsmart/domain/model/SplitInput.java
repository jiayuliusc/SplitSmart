package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: amountCents, basisPoints. Collections are defensive, unmodifiable copies. */
public record SplitInput(
        String participantId,
        Long amountCents,
        Integer basisPoints) {
    public SplitInput {
        Objects.requireNonNull(participantId, "participantId");
    }
}
