package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import edu.usc.splitsmart.util.ImmutableCollections;
import java.util.List;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record LedgerSnapshot(
        String groupId,
        long revision,
        List<String> memberIds,
        List<Expense> expenses,
        List<Settlement> settlements) {
    public LedgerSnapshot {
        Objects.requireNonNull(groupId, "groupId");
        memberIds = ImmutableCollections.list(memberIds);
        expenses = ImmutableCollections.list(expenses);
        settlements = ImmutableCollections.list(settlements);
    }
}
