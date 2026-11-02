package edu.usc.splitsmart.domain.contract;

import java.util.Objects;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record ReadResult<T>(
        T value,
        ReadSource source) {
    public ReadResult {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
    }
}
