package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record PairBalance(
        String otherUserId,
        long netCents) {
    public PairBalance {
        Objects.requireNonNull(otherUserId, "otherUserId");
    }
}
