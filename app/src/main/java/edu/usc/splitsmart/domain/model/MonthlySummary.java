package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import edu.usc.splitsmart.util.ImmutableCollections;
import java.time.YearMonth;
import java.util.Map;

/** Contract v1. Nullable components: none. Collections are defensive, unmodifiable copies. */
public record MonthlySummary(
        String groupId,
        YearMonth month,
        long revision,
        int expenseCount,
        long totalCents,
        Map<Category, Long> categoryTotalsCents) {
    public MonthlySummary {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(month, "month");
        categoryTotalsCents = ImmutableCollections.map(categoryTotalsCents);
    }
}
