package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import java.time.Instant;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record User(
        String uid,
        String name,
        String email,
        String uscId,
        Instant createdAt,
        Instant updatedAt) {
    public User {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(uscId, "uscId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public String toString() {
        return "User[private profile]";
    }
}
