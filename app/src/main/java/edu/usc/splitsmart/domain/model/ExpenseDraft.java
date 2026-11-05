package edu.usc.splitsmart.domain.model;

import java.util.Objects;
import edu.usc.splitsmart.util.ImmutableCollections;
import java.util.List;

/** Contract v1. Nullable components: note. Collections are defensive, unmodifiable copies. */
public record ExpenseDraft(
        String title,
        long amountCents,
        String payerId,
        List<String> participantIds,
        SplitMethod splitMethod,
        Category category,
        String note,
        List<SplitInput> splitInputs) {
    public ExpenseDraft {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(payerId, "payerId");
        participantIds = ImmutableCollections.list(participantIds);
        Objects.requireNonNull(splitMethod, "splitMethod");
        if (category == null) {
            category = Category.OTHER;
        }
        splitInputs = ImmutableCollections.list(splitInputs);
    }
}
