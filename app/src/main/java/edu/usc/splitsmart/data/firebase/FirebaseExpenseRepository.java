package edu.usc.splitsmart.data.firebase;

import java.time.LocalDate;
import java.util.*;
import edu.usc.splitsmart.data.repository.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.util.PendingOperation;

public final class FirebaseExpenseRepository implements ExpenseRepository, LedgerRepository {
    private final FirebaseRepositoryContext context;

    public FirebaseExpenseRepository(FirebaseRepositoryContext context) {
        this.context = context;
    }

    @Override
    public AsyncOperation getExpense(String groupId, String expenseId, ReadPolicy policy, Callback<ReadResult<Expense>> callback) {
        return getLedger(groupId, policy, new Callback<>() {
            @Override public void onSuccess(ReadResult<LedgerSnapshot> result) {
                for (Expense expense : result.value().expenses()) {
                    if (expense.expenseId().equals(expenseId)) {
                        callback.onSuccess(new ReadResult<>(expense, result.source()));
                        return;
                    }
                }
                callback.onError(FirebaseErrors.error(ErrorCode.NOT_FOUND, "This expense no longer exists."));
            }
            @Override public void onError(AppError error) { callback.onError(error); }
        });
    }

    @Override
    public AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback) {
        PendingOperation<ReadResult<LedgerSnapshot>> op = context.operation(callback);
        if (context.scope.uid() == null || context.cached("ledger:" + groupId, policy, op)) return op;
        Map<String, Object> payload = new HashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("groupId", groupId);
        op.attach(context.call("getGroupLedger", payload,
            value -> new ReadResult<>(ExpenseCodec.ledger(value), ReadSource.SERVER),
            context.cache("ledger:" + groupId, op)));
        return op;
    }

    private Map<String, Object> baseRequest(String groupId, long expectedRevision, String requestId) {
        Map<String, Object> req = new HashMap<>();
        req.put("schemaVersion", 1);
        req.put("requestId", requestId);
        req.put("groupId", groupId);
        req.put("expectedRevision", Long.toString(expectedRevision));
        return req;
    }

    @Override
    public AsyncOperation createExpense(String groupId, long expectedRevision, ExpenseDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Expense>> callback) {
        Map<String, Object> req = baseRequest(groupId, expectedRevision, requestId);
        req.put("localDate", localDate.toString());
        req.put("draft", ExpenseCodec.wireDraft(draft));
        return context.call("createExpense", req, value -> context.mutation(value, requestId, true, ExpenseCodec::expense), callback);
    }

    @Override
    public AsyncOperation updateExpense(String groupId, long expectedRevision, String expenseId, ExpenseDraft draft, String requestId, Callback<MutationResult<Expense>> callback) {
        Map<String, Object> req = baseRequest(groupId, expectedRevision, requestId);
        req.put("expenseId", expenseId);
        req.put("draft", ExpenseCodec.wireDraft(draft));
        return context.call("updateExpense", req, value -> context.mutation(value, requestId, true, ExpenseCodec::expense), callback);
    }

    @Override
    public AsyncOperation deleteExpense(String groupId, long expectedRevision, String expenseId, String requestId, Callback<MutationResult<DeletedRecord>> callback) {
        Map<String, Object> req = baseRequest(groupId, expectedRevision, requestId);
        req.put("expenseId", expenseId);
        return context.call("deleteExpense", req, value -> context.mutation(value, requestId, true, result -> {
            Map<?, ?> data = IdentityCodec.map(result);
            return new DeletedRecord(IdentityCodec.string(data, "recordId"));
        }), callback);
    }
}
