package edu.usc.splitsmart.presentation.expenses;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import edu.usc.splitsmart.domain.calculation.ExpenseCalculation;
import edu.usc.splitsmart.domain.contract.ContractVersion;
import edu.usc.splitsmart.domain.model.*;

public final class ExpenseCalculationTest {
    private List<ExpenseShare> percentage(long total, List<String> participants, List<SplitInput> inputs) {
        return ExpenseCalculation.computeShares(new ExpenseDraft("Dinner", total, "a", participants,
            SplitMethod.PERCENTAGE, Category.FOOD, null, inputs));
    }
    @Test public void remainderGoesToLargestFractionRatherThanFirstUid() {
        assertEquals(List.of(new ExpenseShare("a", 0, 1000), new ExpenseShare("b", 1, 9000)),
            percentage(1, List.of("b", "a"), List.of(new SplitInput("b", null, 9000), new SplitInput("a", null, 1000))));
    }
    @Test public void maximumContractTotalDoesNotOverflowPercentageMultiplication() {
        long max = ContractVersion.MAX_ABSOLUTE_CENTS;
        List<ExpenseShare> shares = percentage(max, List.of("b", "a"),
            List.of(new SplitInput("a", null, 5000), new SplitInput("b", null, 5000)));
        assertEquals(max, Math.addExact(shares.get(0).amountCents(), shares.get(1).amountCents()));
        assertEquals(max / 2 + max % 2, shares.get(0).amountCents());
        assertEquals(max / 2, shares.get(1).amountCents());
    }
    @Test public void equalFractionRemaindersUseUidAndOutputRemainsUidSorted() {
        assertEquals(List.of(new ExpenseShare("a", 1, 2500), new ExpenseShare("b", 0, 2500), new ExpenseShare("c", 1, 5000)),
            percentage(2, List.of("c", "b", "a"), List.of(new SplitInput("c", null, 5000),
                new SplitInput("b", null, 2500), new SplitInput("a", null, 2500))));
    }
    @Test public void multipleLeftoverCentsFollowDescendingFractionWithUidTies() {
        assertEquals(List.of(new ExpenseShare("a", 0, 1000), new ExpenseShare("b", 1, 3000), new ExpenseShare("c", 2, 6000)),
            percentage(3, List.of("a", "b", "c"), List.of(new SplitInput("a", null, 1000),
                new SplitInput("b", null, 3000), new SplitInput("c", null, 6000))));
    }
}
