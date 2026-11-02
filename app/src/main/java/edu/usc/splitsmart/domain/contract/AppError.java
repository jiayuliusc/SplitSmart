package edu.usc.splitsmart.domain.contract;

import java.util.Objects;

/** Contract v1. Nullable components: field. Collections are defensive, unmodifiable copies. */
public record AppError(
        ErrorCode code,
        String message,
        String field,
        boolean retryable) {
    public AppError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
