package edu.usc.splitsmart.config;

import edu.usc.splitsmart.service.*;

/** Feature dependencies supplied by the composition root; settlement/history remain unavailable. */
public interface FeatureServices extends IdentityGroupServices {
    ExpenseService expenses();
    BalanceService balances();
    GroupUpdatesService groupUpdates();
    SettlementService settlements();
    HistoryService history();
}
