package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import edu.usc.splitsmart.util.ImmutableCollections;
import java.util.List;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record BalanceSummary(
        String groupId,
        String viewerId,
        long revision,
        List<PairBalance> pairs,
        long totalToPayCents,
        long totalToReceiveCents) {
    public BalanceSummary {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(viewerId, "viewerId");
        pairs = ImmutableCollections.list(pairs);
    }
}
