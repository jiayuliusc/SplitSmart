package edu.usc.splitsmart.domain;

import org.junit.Test;
import java.util.List;
import edu.usc.splitsmart.domain.contract.AppError;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.domain.validation.ExpenseValidation;

import static org.junit.Assert.*;

public class ExpenseValidationTest {

    @Test
    public void testValidExpenseWithPayerInParticipants() {
        ExpenseDraft draft = new ExpenseDraft(
            "Lunch", 2000L, "user1", List.of("user1", "user2"), SplitMethod.EQUAL, Category.FOOD, null, List.of()
        );
        AppError error = ExpenseValidation.expense(draft);
        assertNull(error);
    }

    @Test
    public void testExpenseFailsWhenPayerExcludedFromParticipants() {
        ExpenseDraft draft = new ExpenseDraft(
            "Lunch", 2000L, "user1", List.of("user2", "user3"), SplitMethod.EQUAL, Category.FOOD, null, List.of()
        );
        AppError error = ExpenseValidation.expense(draft);
        assertNotNull(error);
        assertEquals("participantIds", error.field());
    }

    @Test
    public void testExpenseDefaultsNullCategoryToOther() {
        ExpenseDraft draft = new ExpenseDraft(
            "Lunch", 2000L, "user1", List.of("user1", "user2"), SplitMethod.EQUAL, null, null, List.of()
        );
        assertEquals(Category.OTHER, draft.category());
        AppError error = ExpenseValidation.expense(draft);
        assertNull(error);
    }
}
