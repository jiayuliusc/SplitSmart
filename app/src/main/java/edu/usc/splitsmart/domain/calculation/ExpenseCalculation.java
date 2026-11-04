package edu.usc.splitsmart.domain.calculation;

import java.util.*;
import java.math.BigInteger;
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
            record Allocation(String participantId, long cents, int basisPoints, int fraction) { }
            List<Allocation> allocations = new ArrayList<>();
            long sumAllocated = 0L;
            BigInteger denominator = BigInteger.valueOf(10000);
            for (String participantId : sorted) {
                SplitInput input = draft.splitInputs().stream()
                    .filter(value -> participantId.equals(value.participantId()))
                    .findFirst().orElse(null);
                int basisPoints = input != null && input.basisPoints() != null ? input.basisPoints() : 0;
                BigInteger[] quotientAndRemainder = BigInteger.valueOf(total)
                    .multiply(BigInteger.valueOf(basisPoints)).divideAndRemainder(denominator);
                BigInteger quotient = quotientAndRemainder[0];
                if (quotient.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0
                        || quotient.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                    throw new ArithmeticException("Percentage share exceeds the supported integer range.");
                }
                long cents = quotient.longValue();
                sumAllocated = Math.addExact(sumAllocated, cents);
                // Division by 10,000 guarantees the remainder fits in an int.
                allocations.add(new Allocation(participantId, cents, basisPoints, quotientAndRemainder[1].intValue()));
            }
            int remaining = Math.toIntExact(Math.subtractExact(total, sumAllocated));
            List<Allocation> ranked = new ArrayList<>(allocations);
            ranked.sort(Comparator.comparingInt(Allocation::fraction).reversed().thenComparing(Allocation::participantId));
            Set<String> extraCent = new HashSet<>();
            for (int i = 0; i < remaining; i++) extraCent.add(ranked.get(i).participantId());
            List<ExpenseShare> shares = new ArrayList<>();
            // Keep wire output in UID order, independent of fractional-remainder ranking.
            for (Allocation allocation : allocations) {
                shares.add(new ExpenseShare(allocation.participantId(),
                    Math.addExact(allocation.cents(), extraCent.contains(allocation.participantId()) ? 1L : 0L), allocation.basisPoints()));
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
