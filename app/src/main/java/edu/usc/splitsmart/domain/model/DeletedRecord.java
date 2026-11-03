package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record DeletedRecord(
        String recordId) {
    public DeletedRecord {
        Objects.requireNonNull(recordId, "recordId");
    }
}
