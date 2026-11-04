package edu.usc.splitsmart.service.settlements;

import java.time.LocalDate;
import java.util.concurrent.Executor;
import edu.usc.splitsmart.data.repository.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.service.SettlementService;
import edu.usc.splitsmart.util.PendingOperation;

public final class DefaultSettlementService implements SettlementService {
    private final SettlementRepository settlements;
    private final LedgerRepository ledger;
    private final Executor delivery;
    public DefaultSettlementService(SettlementRepository settlements, LedgerRepository ledger, Executor delivery) {
        this.settlements = settlements; this.ledger = ledger; this.delivery = delivery;
    }
    private <T> AsyncOperation invalid(AppError error, Callback<T> callback) {
        PendingOperation<T> operation = new PendingOperation<>(delivery, callback);
        operation.onError(error); return operation;
    }
    @Override public AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback) {
        return ledger.getLedger(groupId, policy, callback);
    }
    @Override public AsyncOperation getSettlement(String groupId, String id, ReadPolicy policy, Callback<ReadResult<Settlement>> callback) {
        return settlements.getSettlement(groupId, id, policy, callback);
    }
    @Override public AsyncOperation createSettlement(String groupId, long revision, SettlementDraft draft, LocalDate date, String requestId, Callback<MutationResult<Settlement>> callback) {
        AppError error = SettlementRules.validate(draft);
        return error == null ? settlements.createSettlement(groupId, revision, draft, date, requestId, callback) : invalid(error, callback);
    }
    @Override public AsyncOperation updateSettlement(String groupId, long revision, String id, SettlementDraft draft, String requestId, Callback<MutationResult<Settlement>> callback) {
        AppError error = SettlementRules.validate(draft);
        return error == null ? settlements.updateSettlement(groupId, revision, id, draft, requestId, callback) : invalid(error, callback);
    }
    @Override public AsyncOperation deleteSettlement(String groupId, long revision, String id, String requestId, Callback<MutationResult<DeletedRecord>> callback) {
        return settlements.deleteSettlement(groupId, revision, id, requestId, callback);
    }
}
