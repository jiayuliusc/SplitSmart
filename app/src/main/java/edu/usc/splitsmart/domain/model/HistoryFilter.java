package edu.usc.splitsmart.domain.model;

import java.util.Objects;

/** Contract v1. Nullable components: type, category, memberId. Collections are defensive, unmodifiable copies. */
public record HistoryFilter(
        String keyword,
        TransactionType type,
        Category category,
        String memberId,
        HistorySort sort) {
    public HistoryFilter {
        Objects.requireNonNull(keyword, "keyword");
        Objects.requireNonNull(sort, "sort");
    }
}
