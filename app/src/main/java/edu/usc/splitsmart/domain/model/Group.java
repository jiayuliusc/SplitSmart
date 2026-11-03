package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import edu.usc.splitsmart.util.ImmutableCollections;
import java.time.Instant;
import java.util.List;

/** Contract v1. Nullable components: description. Collections are defensive, unmodifiable copies. */
public record Group(
        String groupId,
        String name,
        String description,
        String ownerId,
        List<String> memberIds,
        long revision,
        GroupStatus status,
        Instant createdAt,
        Instant updatedAt) {
    public Group {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ownerId, "ownerId");
        memberIds = ImmutableCollections.list(memberIds);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
