package edu.usc.splitsmart.config;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import edu.usc.splitsmart.data.firebase.*;
import edu.usc.splitsmart.service.*;
import edu.usc.splitsmart.service.expenses.DefaultExpenseService;
import edu.usc.splitsmart.service.balances.DefaultBalanceService;
import edu.usc.splitsmart.service.identity.DefaultAuthService;
import edu.usc.splitsmart.service.groups.DefaultGroupService;
import edu.usc.splitsmart.service.groups.DefaultGroupUpdatesService;
import edu.usc.splitsmart.service.settlements.DefaultSettlementService;

/** Manual dependency wiring; add agreed repository/service instances as features arrive. */
public final class AppContainer implements FeatureServices {
    private final FirebaseClients firebaseClients;
    private final AuthService auth;
    private final GroupService groups;
    private final ExpenseService expenses;
    private final BalanceService balances;
    private final SettlementService settlements;
    private final GroupUpdatesService groupUpdates;

    AppContainer(Context context) {
        firebaseClients = new FirebaseClients(context);
        Handler main = new Handler(Looper.getMainLooper());
        Executor delivery = command -> main.post(command);
        FirebaseRepositoryContext repositories = new FirebaseRepositoryContext(firebaseClients.getAuth(),
            firebaseClients.getFirestore(), firebaseClients.getFunctions(), delivery,
            Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "splitsmart-retries"); t.setDaemon(true); return t; }));
        auth = new DefaultAuthService(new FirebaseAuthRepository(firebaseClients.getAuth(), delivery),
            new FirebaseUserRepository(repositories), delivery);
        groups = new DefaultGroupService(new FirebaseGroupRepository(repositories), delivery);
        FirebaseExpenseRepository expenseRepo = new FirebaseExpenseRepository(repositories);
        expenses = new DefaultExpenseService(expenseRepo, expenseRepo, delivery);
        settlements = new DefaultSettlementService(new FirebaseSettlementRepository(repositories,expenseRepo),expenseRepo,delivery);
        groupUpdates = new DefaultGroupUpdatesService(new FirebaseGroupUpdatesRepository(repositories),delivery);
        balances = new DefaultBalanceService(auth, expenseRepo,
            Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "splitsmart-balances"); t.setDaemon(true); return t; }), delivery, uid -> uid.equals(firebaseClients.getAuth().getUid()));
    }

    /** Infrastructure seam for repository construction and the local instrumentation check. */
    public FirebaseClients getFirebaseClients() {
        return firebaseClients;
    }
    @Override public AuthService auth() { return auth; }
    @Override public GroupService groups() { return groups; }
    @Override public ExpenseService expenses() { return expenses; }
    @Override public BalanceService balances() { return balances; }
    @Override public SettlementService settlements() { return settlements; }
    @Override public GroupUpdatesService groupUpdates() { return groupUpdates; }
    @Override public HistoryService history() { return null; }
}
