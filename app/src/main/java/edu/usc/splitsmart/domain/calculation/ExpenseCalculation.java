package edu.usc.splitsmart.domain.calculation;

import java.util.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.util.ImmutableCollections;

public final class ExpenseCalculation {
    private ExpenseCalculation() { }

    public static List<ExpenseShare> computeShares(ExpenseDraft draft) {
        Objects.requireNonNull(draft, "draft");
        List<String> sorted = new ArrayList<>(draft.participantIds());
        Collections.sort(sorted);
        long total = draft.amountCents();

        if (draft.splitMethod() == SplitMethod.EXACT && !draft.splitInputs().isEmpty()) {
            List<ExpenseShare> shares = new ArrayList<>();
            for (String pId : sorted) {
                SplitInput input = draft.splitInputs().stream()
                    .filter(i -> pId.equals(i.participantId()))
                    .findFirst()
                    .orElse(null);
                long amt = input != null && input.amountCents() != null ? input.amountCents() : 0L;
                shares.add(new ExpenseShare(pId, amt, null));
            }
            return ImmutableCollections.list(shares);
        }

        if (draft.splitMethod() == SplitMethod.PERCENTAGE && !draft.splitInputs().isEmpty()) {
            List<ExpenseShare> shares = new ArrayList<>();
            long sumAllocated = 0L;
            List<Long> baseAmts = new ArrayList<>();
            for (String pId : sorted) {
                SplitInput input = draft.splitInputs().stream()
                    .filter(i -> pId.equals(i.participantId()))
                    .findFirst()
                    .orElse(null);
                int bp = input != null && input.basisPoints() != null ? input.basisPoints() : 0;
                long amt = (total * bp) / 10000L;
                sumAllocated += amt;
                baseAmts.add(amt);
            }
            int remainder = (int) (total - sumAllocated);
            for (int i = 0; i < sorted.size(); i++) {
                String pId = sorted.get(i);
                SplitInput input = draft.splitInputs().stream()
                    .filter(in -> pId.equals(in.participantId()))
                    .findFirst()
                    .orElse(null);
                Integer bp = input != null ? input.basisPoints() : null;
                long extra = i < remainder ? 1L : 0L;
                shares.add(new ExpenseShare(pId, baseAmts.get(i) + extra, bp));
            }
            return ImmutableCollections.list(shares);
        }

        // Default: EQUAL
        int count = sorted.size();
        long base = total / count;
        int remainder = (int) (total % count);
        List<ExpenseShare> shares = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long amt = base + (i < remainder ? 1L : 0L);
            shares.add(new ExpenseShare(sorted.get(i), amt, null));
        }
        return ImmutableCollections.list(shares);
    }
}
