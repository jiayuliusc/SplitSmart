package edu.usc.splitsmart.service.expenses;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import edu.usc.splitsmart.data.repository.*;
import edu.usc.splitsmart.domain.calculation.ExpenseCalculation;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.domain.validation.ExpenseValidation;
import edu.usc.splitsmart.service.ExpenseService;
import edu.usc.splitsmart.util.PendingOperation;

public final class DefaultExpenseService implements ExpenseService {
    private final ExpenseRepository expenses;
    private final LedgerRepository ledger;
    private final Executor delivery;

    public DefaultExpenseService(ExpenseRepository expenses, LedgerRepository ledger, Executor delivery) {
        this.expenses = expenses;
        this.ledger = ledger;
        this.delivery = delivery;
    }

    private <T> AsyncOperation invalid(AppError error, Callback<T> callback) {
        PendingOperation<T> op = new PendingOperation<>(delivery, callback);
        op.onError(error);
        return op;
    }

    @Override
    public AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback) {
        return ledger.getLedger(groupId, policy, callback);
    }

    @Override
    public AsyncOperation previewSplit(ExpenseDraft draft, Callback<List<ExpenseShare>> callback) {
        AppError error = ExpenseValidation.expense(draft);
        PendingOperation<List<ExpenseShare>> op = new PendingOperation<>(delivery, callback);
        if (error != null) {
            op.onError(error);
        } else {
            op.onSuccess(ExpenseCalculation.computeShares(draft));
        }
        return op;
    }

    @Override
    public AsyncOperation getExpense(String groupId, String expenseId, ReadPolicy policy, Callback<ReadResult<Expense>> callback) {
        return expenses.getExpense(groupId, expenseId, policy, callback);
    }

    @Override
    public AsyncOperation createExpense(String groupId, long expectedRevision, ExpenseDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Expense>> callback) {
        AppError error = ExpenseValidation.expense(draft);
        return error == null ? expenses.createExpense(groupId, expectedRevision, draft, localDate, requestId, callback) : invalid(error, callback);
    }

    @Override
    public AsyncOperation updateExpense(String groupId, long expectedRevision, String expenseId, ExpenseDraft draft, String requestId, Callback<MutationResult<Expense>> callback) {
        AppError error = ExpenseValidation.expense(draft);
        return error == null ? expenses.updateExpense(groupId, expectedRevision, expenseId, draft, requestId, callback) : invalid(error, callback);
    }

    @Override
    public AsyncOperation deleteExpense(String groupId, long expectedRevision, String expenseId, String requestId, Callback<MutationResult<DeletedRecord>> callback) {
        return expenses.deleteExpense(groupId, expectedRevision, expenseId, requestId, callback);
    }
}
