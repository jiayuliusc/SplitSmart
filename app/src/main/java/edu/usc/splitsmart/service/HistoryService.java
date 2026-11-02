package edu.usc.splitsmart.service;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import java.util.List;
import java.time.YearMonth;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface HistoryService {
    AsyncOperation getHistory(String groupId, HistoryFilter filter, ReadPolicy policy, Callback<ReadResult<List<HistoryItem>>> callback);

    AsyncOperation getMonthlySummary(String groupId, YearMonth month, ReadPolicy policy, Callback<ReadResult<MonthlySummary>> callback);
}
