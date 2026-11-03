package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record ProfileDraft(
        String name,
        String uscId) {
    public ProfileDraft {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(uscId, "uscId");
    }

    @Override
    public String toString() {
        return "ProfileDraft[private profile]";
    }
}
