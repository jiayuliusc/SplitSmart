package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record GroupDeletion(
        String groupId,
        GroupStatus status) {
    public GroupDeletion {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(status, "status");
    }
}
