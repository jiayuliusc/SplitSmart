package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: description. Collections are defensive, unmodifiable copies. */
public record GroupDraft(
        String name,
        String description) {
    public GroupDraft {
        Objects.requireNonNull(name, "name");
    }
}
