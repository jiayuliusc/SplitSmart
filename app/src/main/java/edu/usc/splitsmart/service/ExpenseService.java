package edu.usc.splitsmart.service;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import java.time.LocalDate;
import java.util.List;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface ExpenseService {
    AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback);

    AsyncOperation previewSplit(ExpenseDraft draft, Callback<List<ExpenseShare>> callback);

    AsyncOperation getExpense(String groupId, String expenseId, ReadPolicy policy, Callback<ReadResult<Expense>> callback);

    AsyncOperation createExpense(String groupId, long expectedRevision, ExpenseDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Expense>> callback);

    AsyncOperation updateExpense(String groupId, long expectedRevision, String expenseId, ExpenseDraft draft, String requestId, Callback<MutationResult<Expense>> callback);

    AsyncOperation deleteExpense(String groupId, long expectedRevision, String expenseId, String requestId, Callback<MutationResult<DeletedRecord>> callback);
}
