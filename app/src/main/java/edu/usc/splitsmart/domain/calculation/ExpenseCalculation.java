package edu.usc.splitsmart.domain.calculation;

import java.math.BigInteger;
import java.util.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.util.ImmutableCollections;

public final class ExpenseCalculation {
    private ExpenseCalculation() { }

    public static List<ExpenseShare> computeShares(ExpenseDraft draft) {
        Objects.requireNonNull(draft, "draft");
        List<String> sorted = new ArrayList<>(draft.participantIds());
        Collections.sort(sorted);
        if (sorted.size() < 2 || new HashSet<>(sorted).size() != sorted.size()) {
            throw new IllegalArgumentException("At least two distinct participants are required.");
        }
        long total = draft.amountCents();
        if (total <= 0) throw new IllegalArgumentException("Expense total must be positive.");
        Map<String, SplitInput> inputs = indexedInputs(draft, sorted);

        if (draft.splitMethod() == SplitMethod.EXACT) {
            List<ExpenseShare> shares = new ArrayList<>();
            BigInteger sum = BigInteger.ZERO;
            for (String pId : sorted) {
                SplitInput input = inputs.get(pId);
                if (input == null || input.amountCents() == null || input.basisPoints() != null
                    || input.amountCents() < 0) {
                    throw new IllegalArgumentException("Missing or invalid exact split for " + pId + ".");
                }
                long amt = input.amountCents();
                sum = sum.add(BigInteger.valueOf(amt));
                shares.add(new ExpenseShare(pId, amt, null));
            }
            if (!sum.equals(BigInteger.valueOf(total))) {
                throw new IllegalArgumentException("Exact split amounts must equal the expense total.");
            }
            return ImmutableCollections.list(shares);
        }

        if (draft.splitMethod() == SplitMethod.PERCENTAGE) {
            record Allocation(String participantId, BigInteger cents, int basisPoints, int fraction) { }
            BigInteger totalValue = BigInteger.valueOf(total);
            BigInteger sumAllocated = BigInteger.ZERO;
            BigInteger denominator = BigInteger.valueOf(10000);
            List<Allocation> allocations = new ArrayList<>();
            int basisPointTotal = 0;
            for (String pId : sorted) {
                SplitInput input = inputs.get(pId);
                if (input == null || input.basisPoints() == null || input.amountCents() != null
                    || input.basisPoints() < 0 || input.basisPoints() > 10000) {
                    throw new IllegalArgumentException("Missing or invalid percentage split for " + pId + ".");
                }
                int bp = input.basisPoints();
                basisPointTotal += bp;
                BigInteger[] quotientAndRemainder = totalValue.multiply(BigInteger.valueOf(bp))
                    .divideAndRemainder(denominator);
                BigInteger amt = quotientAndRemainder[0];
                sumAllocated = sumAllocated.add(amt);
                allocations.add(new Allocation(pId, amt, bp, quotientAndRemainder[1].intValue()));
            }
            if (basisPointTotal != 10000) {
                throw new IllegalArgumentException("Percentages must total exactly 100%.");
            }
            BigInteger remainderValue = totalValue.subtract(sumAllocated);
            if (remainderValue.signum() < 0
                || remainderValue.compareTo(BigInteger.valueOf(sorted.size())) >= 0) {
                throw new ArithmeticException("Invalid percentage rounding remainder.");
            }
            int remainder = remainderValue.intValue();
            List<Allocation> ranked = new ArrayList<>(allocations);
            ranked.sort(Comparator.comparingInt(Allocation::fraction).reversed()
                .thenComparing(Allocation::participantId));
            Set<String> extraCent = new HashSet<>();
            for (int i = 0; i < remainder; i++) extraCent.add(ranked.get(i).participantId());

            List<ExpenseShare> shares = new ArrayList<>();
            for (Allocation allocation : allocations) {
                BigInteger amount = allocation.cents().add(
                    extraCent.contains(allocation.participantId()) ? BigInteger.ONE : BigInteger.ZERO);
                if (amount.signum() < 0 || amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                    throw new ArithmeticException("Calculated share exceeds the supported range.");
                }
                shares.add(new ExpenseShare(allocation.participantId(), amount.longValue(), allocation.basisPoints()));
            }
            return ImmutableCollections.list(shares);
        }

        // Default: EQUAL
        if (draft.splitMethod() != SplitMethod.EQUAL || !draft.splitInputs().isEmpty()) {
            throw new IllegalArgumentException("Invalid equal split inputs.");
        }
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

    private static Map<String, SplitInput> indexedInputs(ExpenseDraft draft, List<String> participants) {
        Map<String, SplitInput> result = new HashMap<>();
        Set<String> selected = new HashSet<>(participants);
        for (SplitInput input : draft.splitInputs()) {
            if (!selected.contains(input.participantId()) || result.put(input.participantId(), input) != null) {
                throw new IllegalArgumentException("Duplicate or unselected split participant.");
            }
        }
        return result;
    }
}
