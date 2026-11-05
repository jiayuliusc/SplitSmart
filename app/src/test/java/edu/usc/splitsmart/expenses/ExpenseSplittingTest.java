package edu.usc.splitsmart.expenses;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import edu.usc.splitsmart.domain.calculation.ExpenseCalculation;
import edu.usc.splitsmart.domain.contract.AppError;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.domain.validation.ExpenseValidation;

public final class ExpenseSplittingTest {
    private static ExpenseDraft draft(long total, SplitMethod method, List<SplitInput> inputs) {
        return new ExpenseDraft("Dinner", total, "A", List.of("C", "A", "B"), method,
            Category.FOOD, null, inputs);
    }

    @Test public void equalSplitAllocatesRemainderInUidOrder() {
        List<ExpenseShare> shares = ExpenseCalculation.computeShares(
            draft(1000, SplitMethod.EQUAL, List.of()));
        assertEquals(List.of(
            new ExpenseShare("A", 334, null),
            new ExpenseShare("B", 333, null),
            new ExpenseShare("C", 333, null)), shares);
    }

    @Test public void exactSplitPreservesEnteredAmounts() {
        ExpenseDraft value = draft(3000, SplitMethod.EXACT, List.of(
            new SplitInput("A", 1000L, null),
            new SplitInput("B", 1500L, null),
            new SplitInput("C", 500L, null)));
        assertNull(ExpenseValidation.expense(value));
        assertEquals(3000, ExpenseCalculation.computeShares(value).stream()
            .mapToLong(ExpenseShare::amountCents).sum());
    }

    @Test public void exactSplitRejectsMismatchedTotal() {
        ExpenseDraft value = draft(3000, SplitMethod.EXACT, List.of(
            new SplitInput("A", 1000L, null),
            new SplitInput("B", 1000L, null),
            new SplitInput("C", 500L, null)));
        AppError error = ExpenseValidation.expense(value);
        assertNotNull(error);
        assertEquals("splitInputs", error.field());
        assertThrows(IllegalArgumentException.class,
            () -> ExpenseCalculation.computeShares(value));
    }

    @Test public void percentageSplitTotalsExpenseAfterRounding() {
        ExpenseDraft value = draft(1001, SplitMethod.PERCENTAGE, List.of(
            new SplitInput("A", null, 2500),
            new SplitInput("B", null, 5000),
            new SplitInput("C", null, 2500)));
        assertNull(ExpenseValidation.expense(value));
        List<ExpenseShare> shares = ExpenseCalculation.computeShares(value);
        assertEquals(1001, shares.stream().mapToLong(ExpenseShare::amountCents).sum());
        assertEquals(250, shares.get(0).amountCents());
        assertEquals(501, shares.get(1).amountCents());
    }

    @Test public void percentageSplitMustTotalExactlyOneHundredPercent() {
        ExpenseDraft value = draft(1000, SplitMethod.PERCENTAGE, List.of(
            new SplitInput("A", null, 3300),
            new SplitInput("B", null, 3300),
            new SplitInput("C", null, 3300)));
        assertNotNull(ExpenseValidation.expense(value));
        assertThrows(IllegalArgumentException.class,
            () -> ExpenseCalculation.computeShares(value));
    }

    @Test public void duplicateAndMissingParticipantInputsAreRejected() {
        ExpenseDraft value = draft(1000, SplitMethod.EXACT, List.of(
            new SplitInput("A", 500L, null),
            new SplitInput("A", 500L, null),
            new SplitInput("B", 0L, null)));
        assertNotNull(ExpenseValidation.expense(value));
        assertThrows(IllegalArgumentException.class,
            () -> ExpenseCalculation.computeShares(value));
    }
}
