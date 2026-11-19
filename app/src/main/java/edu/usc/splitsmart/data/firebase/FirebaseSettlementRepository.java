package edu.usc.splitsmart.data.firebase;

import java.time.LocalDate;
import java.util.*;
import edu.usc.splitsmart.data.repository.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.util.PendingOperation;

public final class FirebaseSettlementRepository implements SettlementRepository {
    private final FirebaseRepositoryContext context;
    private final LedgerRepository ledger;
    public FirebaseSettlementRepository(FirebaseRepositoryContext context, LedgerRepository ledger) {
        this.context = context; this.ledger = ledger;
    }
    @Override public AsyncOperation getSettlement(String groupId, String id, ReadPolicy policy, Callback<ReadResult<Settlement>> callback) {
        PendingOperation<ReadResult<Settlement>> operation = context.operation(callback);
        if (context.scope.uid() == null) return operation;
        operation.attach(ledger.getLedger(groupId, policy, new Callback<>() {
            @Override public void onSuccess(ReadResult<LedgerSnapshot> result) {
                for (Settlement settlement : result.value().settlements()) if (settlement.settlementId().equals(id)) {
                    operation.onSuccess(new ReadResult<>(settlement, result.source())); return;
                }
                operation.onError(FirebaseErrors.error(ErrorCode.NOT_FOUND, "This settlement no longer exists."));
            }
            @Override public void onError(AppError error) { operation.onError(error); }
        }));
        return operation;
    }
    private Map<String, Object> request(String groupId, long revision, String requestId) {
        Map<String, Object> request = new HashMap<>();
        request.put("schemaVersion", 1); request.put("groupId", groupId);
        request.put("expectedRevision", Long.toString(revision)); request.put("requestId", requestId);
        return request;
    }
    private Map<String, Object> draft(SettlementDraft value) {
        Map<String, Object> result = new HashMap<>(); result.put("payeeId", value.payeeId());
        result.put("amountCents", Long.toString(value.amountCents()));
        if (value.payerId() != null) result.put("payerId", value.payerId());
        if (value.note() != null) result.put("note", value.note());
        return result;
    }
    @Override public AsyncOperation createSettlement(String groupId, long revision, SettlementDraft draft, LocalDate date, String requestId, Callback<MutationResult<Settlement>> callback) {
        Map<String, Object> request = request(groupId, revision, requestId);
        request.put("draft", draft(draft)); request.put("localDate", date.toString());
        return context.call("createSettlement", request, value -> context.mutation(value, requestId, true, ExpenseCodec::settlement), callback);
    }
    @Override public AsyncOperation updateSettlement(String groupId, long revision, String id, SettlementDraft draft, String requestId, Callback<MutationResult<Settlement>> callback) {
        Map<String, Object> request = request(groupId, revision, requestId);
        request.put("draft", draft(draft)); request.put("settlementId", id);
        return context.call("updateSettlement", request, value -> context.mutation(value, requestId, true, ExpenseCodec::settlement), callback);
    }
    @Override public AsyncOperation deleteSettlement(String groupId, long revision, String id, String requestId, Callback<MutationResult<DeletedRecord>> callback) {
        Map<String, Object> request = request(groupId, revision, requestId); request.put("settlementId", id);
        return context.call("deleteSettlement", request, value -> context.mutation(value, requestId, true,
            result -> new DeletedRecord(IdentityCodec.string(IdentityCodec.map(result), "recordId"))), callback);
    }
}
