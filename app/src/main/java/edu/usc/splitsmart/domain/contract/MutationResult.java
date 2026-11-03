package edu.usc.splitsmart.domain.contract;

import java.util.Objects;

/** Contract v1. Nullable components: groupRevision. Collections are defensive, unmodifiable copies. */
public record MutationResult<T>(
        String requestId,
        Long groupRevision,
        T value) {
    public MutationResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(value, "value");
    }
}
