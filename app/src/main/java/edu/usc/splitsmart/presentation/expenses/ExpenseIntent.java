package edu.usc.splitsmart.presentation.expenses;

import java.time.LocalDate;
import java.util.*;
import edu.usc.splitsmart.domain.model.*;

/** Complete immutable write input, persisted as Bundle-compatible strings for exact retries. */
record ExpenseIntent(String operation, long revision, String expenseId, LocalDate date, ExpenseDraft draft) {
    static ExpenseDraft draftFrom(Expense expense) {
        List<SplitInput> inputs = new ArrayList<>();
        if (expense.splitMethod() != SplitMethod.EQUAL) {
            for (ExpenseShare share : expense.shares()) {
                inputs.add(new SplitInput(share.participantId(),
                    expense.splitMethod() == SplitMethod.EXACT ? share.amountCents() : null,
                    expense.splitMethod() == SplitMethod.PERCENTAGE ? share.basisPoints() : null));
            }
        }
        return new ExpenseDraft(expense.title(), expense.amountCents(), expense.payerId(), expense.participantIds(),
            expense.splitMethod(), expense.category(), expense.note(), inputs);
    }
    ArrayList<String> save() {
        ArrayList<String> fields = new ArrayList<>(Arrays.asList(operation, Long.toString(revision), expenseId,
            date == null ? null : date.toString()));
        if (draft == null) return fields;
        Collections.addAll(fields, draft.title(), Long.toString(draft.amountCents()), draft.payerId(),
            draft.splitMethod().name(), draft.category().name(), draft.note(), Integer.toString(draft.participantIds().size()));
        fields.addAll(draft.participantIds());
        fields.add(Integer.toString(draft.splitInputs().size()));
        for (SplitInput input : draft.splitInputs()) {
            Collections.addAll(fields, input.participantId(), input.amountCents() == null ? null : input.amountCents().toString(),
                input.basisPoints() == null ? null : input.basisPoints().toString());
        }
        return fields;
    }
    static ExpenseIntent restore(List<String> fields) {
        ExpenseDraft draft = null;
        if (fields.size() > 4) {
            int count = Integer.parseInt(fields.get(10));
            List<String> participants = new ArrayList<>(fields.subList(11, 11 + count));
            int index = 11 + count;
            int inputCount = Integer.parseInt(fields.get(index++));
            List<SplitInput> inputs = new ArrayList<>();
            for (int i = 0; i < inputCount; i++) {
                String uid = fields.get(index++), amount = fields.get(index++), points = fields.get(index++);
                inputs.add(new SplitInput(uid, amount == null ? null : Long.valueOf(amount), points == null ? null : Integer.valueOf(points)));
            }
            draft = new ExpenseDraft(fields.get(4), Long.parseLong(fields.get(5)), fields.get(6), participants,
                SplitMethod.valueOf(fields.get(7)), Category.valueOf(fields.get(8)), fields.get(9), inputs);
        }
        return new ExpenseIntent(fields.get(0), Long.parseLong(fields.get(1)), fields.get(2),
            fields.get(3) == null ? null : LocalDate.parse(fields.get(3)), draft);
    }
}
