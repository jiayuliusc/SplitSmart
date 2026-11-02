package edu.usc.splitsmart.service;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface BalanceService {
    AsyncOperation getBalances(String groupId, ReadPolicy policy, Callback<ReadResult<BalanceSummary>> callback);
}
