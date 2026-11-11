package edu.usc.splitsmart.presentation.settlements;

import java.time.LocalDate;
import java.util.*;
import edu.usc.splitsmart.domain.model.SettlementDraft;

/** SavedStateHandle-compatible immutable payload. The request ID is stored alongside it. */
public record SettlementIntent(String operation, long revision, String settlementId, LocalDate date, SettlementDraft draft) {
    /** Index 7 (payerId) was added after the original seven entries; older saved lists have no index 7. */
    private static final int PAYER_INDEX = 7;

    public ArrayList<String> save() {
        return new ArrayList<>(Arrays.asList(operation, Long.toString(revision), settlementId,
            date == null ? null : date.toString(), draft == null ? null : draft.payeeId(),
            draft == null ? null : Long.toString(draft.amountCents()), draft == null ? null : draft.note(),
            draft == null ? null : draft.payerId()));
    }
    public static SettlementIntent restore(List<String> values) {
        String payerId = values.size() > PAYER_INDEX ? values.get(PAYER_INDEX) : null;
        return new SettlementIntent(values.get(0), Long.parseLong(values.get(1)), values.get(2),
            values.get(3) == null ? null : LocalDate.parse(values.get(3)), values.get(4) == null ? null :
            new SettlementDraft(payerId, values.get(4), Long.parseLong(values.get(5)), values.get(6)));
    }
}
