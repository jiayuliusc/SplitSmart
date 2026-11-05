package edu.usc.splitsmart.presentation.expenses;

import org.junit.Test;
import java.time.*;
import java.util.*;
import edu.usc.splitsmart.domain.model.*;
import static org.junit.Assert.*;

public class ExpenseIntentTest {
    private Expense expense(SplitMethod method, List<ExpenseShare> shares) {
        return new Expense("e", "g", "Dinner", 1234, "USD", "a", "b", List.of("a", "b"), method,
            Category.OTHER, "note", LocalDate.of(2025, 1, 2), Instant.EPOCH, Instant.EPOCH, shares);
    }
    @Test public void preservesExactSplitWhenReconstructingDraft() {
        Expense original = expense(SplitMethod.EXACT, List.of(new ExpenseShare("a", 1000, null), new ExpenseShare("b", 234, null)));
        ExpenseDraft draft = ExpenseIntent.draftFrom(original);
        assertEquals(SplitMethod.EXACT, draft.splitMethod());
        assertEquals(List.of(new SplitInput("a", 1000L, null), new SplitInput("b", 234L, null)), draft.splitInputs());
    }
    @Test public void preservesBasisPointsRatherThanInferringFromRoundedCents() {
        Expense original = expense(SplitMethod.PERCENTAGE, List.of(new ExpenseShare("a", 411, 3333), new ExpenseShare("b", 823, 6667)));
        assertEquals(List.of(new SplitInput("a", null, 3333), new SplitInput("b", null, 6667)), ExpenseIntent.draftFrom(original).splitInputs());
    }
    @Test public void roundTripRetainsCompleteCreatePayloadDateAndRevision() {
        ExpenseDraft draft = new ExpenseDraft("Dinner,\nwith friends", 1234, "b", List.of("a", "b"), SplitMethod.EQUAL, Category.OTHER, null, List.of());
        ExpenseIntent intent = new ExpenseIntent("create", 91, null, LocalDate.of(2025, 1, 2), draft);
        assertEquals(intent, ExpenseIntent.restore(intent.save()));
    }
    @Test public void roundTripRetainsPercentageInputsAndNote() {
        ExpenseDraft draft = ExpenseIntent.draftFrom(expense(SplitMethod.PERCENTAGE, List.of(new ExpenseShare("a", 411, 3333), new ExpenseShare("b", 823, 6667))));
        ExpenseIntent intent = new ExpenseIntent("update", 92, "e", null, draft);
        assertEquals(intent, ExpenseIntent.restore(intent.save()));
    }
    @Test public void roundTripRetainsDeleteIdentityAndRevision() {
        ExpenseIntent intent = new ExpenseIntent("delete", 93, "e", null, null);
        assertEquals(intent, ExpenseIntent.restore(intent.save()));
    }
}
