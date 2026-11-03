package edu.usc.splitsmart.data.repository;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import java.time.LocalDate;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface ExpenseRepository {
    AsyncOperation getExpense(String groupId, String expenseId, ReadPolicy policy, Callback<ReadResult<Expense>> callback);

    AsyncOperation createExpense(String groupId, long expectedRevision, ExpenseDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Expense>> callback);

    AsyncOperation updateExpense(String groupId, long expectedRevision, String expenseId, ExpenseDraft draft, String requestId, Callback<MutationResult<Expense>> callback);

    AsyncOperation deleteExpense(String groupId, long expectedRevision, String expenseId, String requestId, Callback<MutationResult<DeletedRecord>> callback);
}
