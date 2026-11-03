package edu.usc.splitsmart.data.repository;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import java.time.LocalDate;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface SettlementRepository {
    AsyncOperation getSettlement(String groupId, String settlementId, ReadPolicy policy, Callback<ReadResult<Settlement>> callback);

    AsyncOperation createSettlement(String groupId, long expectedRevision, SettlementDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Settlement>> callback);

    AsyncOperation updateSettlement(String groupId, long expectedRevision, String settlementId, SettlementDraft draft, String requestId, Callback<MutationResult<Settlement>> callback);

    AsyncOperation deleteSettlement(String groupId, long expectedRevision, String settlementId, String requestId, Callback<MutationResult<DeletedRecord>> callback);
}
