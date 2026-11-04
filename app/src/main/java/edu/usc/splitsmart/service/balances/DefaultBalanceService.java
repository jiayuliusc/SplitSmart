package edu.usc.splitsmart.service.balances;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import edu.usc.splitsmart.data.repository.LedgerRepository;
import edu.usc.splitsmart.domain.calculation.BalanceCalculator;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.service.*;
import edu.usc.splitsmart.util.PendingOperation;

/** Authenticated, cancellable balance projection. Calculation never runs on the delivery thread. */
public final class DefaultBalanceService implements BalanceService {
    private final AuthService auth;
    private final LedgerRepository ledger;
    private final Executor worker;
    private final Executor delivery;
    private final Predicate<String> currentUser;
    public DefaultBalanceService(AuthService auth, LedgerRepository ledger, Executor worker, Executor delivery, Predicate<String> currentUser) {
        this.auth=auth; this.ledger=ledger; this.worker=worker; this.delivery=delivery; this.currentUser=currentUser;
    }
    @Override public AsyncOperation getBalances(String groupId, ReadPolicy policy, Callback<ReadResult<BalanceSummary>> callback) {
        Request request = new Request(groupId, policy, callback);
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,128}") || policy == null) {
            request.operation.onError(new AppError(ErrorCode.VALIDATION,"Choose a group to view balances.","groupId",false));
        } else request.operation.attach(auth.observeSession(request));
        return request.operation;
    }
    private final class Request implements SessionObserver {
        private final String groupId;
        private final ReadPolicy policy;
        private final PendingOperation<ReadResult<BalanceSummary>> operation;
        private boolean started;
        private String viewer;
        Request(String groupId, ReadPolicy policy, Callback<ReadResult<BalanceSummary>> callback) {
            this.groupId=groupId; this.policy=policy;
            operation = new PendingOperation<>(delivery, new Callback<>() {
                @Override public void onSuccess(ReadResult<BalanceSummary> value) {
                    try { callback.onSuccess(value); } finally { cleanUp(); }
                }
                @Override public void onError(AppError error) {
                    try { callback.onError(error); } finally { cleanUp(); }
                }
            }, this::isCurrent);
        }
        private boolean isCurrent() {
            if (!started || viewer == null || currentUser.test(viewer)) return true;
            cleanUp();
            return false;
        }
        private void cleanUp() {
            // Do not hold the parent delivery lock while cancelling child operations.
            delivery.execute(operation::cancel);
        }
        @Override public void onSessionChanged(Session session) {
            if (operation.isCancelled()) return;
            if (started) {
                if (!Objects.equals(viewer, session.uid())) operation.cancel();
                return;
            }
            started=true; viewer=session.uid();
            if (viewer == null) {
                operation.onError(new AppError(ErrorCode.UNAUTHENTICATED,"Sign in to view balances.",null,false)); return;
            }
            operation.attach(ledger.getLedger(groupId,policy,new Callback<>() {
                @Override public void onSuccess(ReadResult<LedgerSnapshot> result) {
                    worker.execute(() -> {
                        if (operation.isCancelled()) return;
                        try {
                            if (!groupId.equals(result.value().groupId())) throw new IllegalArgumentException("Wrong ledger scope");
                            operation.onSuccess(new ReadResult<>(BalanceCalculator.calculate(result.value(),viewer),result.source()));
                        } catch (ArithmeticException error) {
                            operation.onError(new AppError(ErrorCode.OUT_OF_RANGE,"Balances exceed the supported amount range.",null,false));
                        } catch (IllegalArgumentException error) {
                            operation.onError(new AppError(ErrorCode.INTERNAL,"Could not read a complete group ledger.",null,false));
                        }
                    });
                }
                @Override public void onError(AppError error) { operation.onError(error); }
            }));
        }
        @Override public void onError(AppError error) { operation.onError(error); }
    }
}
