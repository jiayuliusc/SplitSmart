package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record MemberProfile(
        String uid,
        String name,
        String email) {
    public MemberProfile {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
    }
}
