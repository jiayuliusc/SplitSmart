package edu.usc.splitsmart.data.repository;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface LedgerRepository {
    AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback);
}
