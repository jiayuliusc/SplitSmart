package edu.usc.splitsmart.config;

import edu.usc.splitsmart.service.*;

/** Future real composition root implements this interface; the placeholder does not. */
public interface FeatureServices extends IdentityGroupServices {
    ExpenseService expenses();
    BalanceService balances();
    SettlementService settlements();
    HistoryService history();
}
