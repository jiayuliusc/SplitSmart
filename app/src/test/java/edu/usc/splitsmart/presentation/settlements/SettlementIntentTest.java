package edu.usc.splitsmart.presentation.settlements;
import org.junit.Test;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import edu.usc.splitsmart.domain.model.SettlementDraft;
import static org.junit.Assert.*;
public class SettlementIntentTest {
    @Test public void createRoundTripRetainsExactPayloadAcrossMidnight() {
        SettlementIntent intent = new SettlementIntent("create", 7, null, LocalDate.of(2026,11,4),new SettlementDraft(null, "a",1234,"Thanks"));
        assertEquals(intent, SettlementIntent.restore(intent.save()));
    }
    @Test public void updateAndDeleteKeepOriginalRevisionAndRecord() {
        for (String operation : new String[]{"update", "delete"}) {
            SettlementIntent intent = new SettlementIntent(operation, 8, "s", null,
                "delete".equals(operation) ? null : new SettlementDraft(null, "a",100,null));
            assertEquals(intent, SettlementIntent.restore(intent.save()));
        }
    }
    @Test public void payerSurvivesSaveAndRestore() {
        SettlementIntent intent = new SettlementIntent("create", 7, null, LocalDate.of(2026, 11, 11),
            new SettlementDraft("b", "a", 500, null));
        assertEquals(intent, SettlementIntent.restore(intent.save()));
        assertEquals("b", SettlementIntent.restore(intent.save()).draft().payerId());
    }

    @Test public void olderSevenEntryStateRestoresWithoutPayer() {
        List<String> old = Arrays.asList("create", "7", null, "2026-11-11", "a", "500", null);
        assertNull(SettlementIntent.restore(old).draft().payerId());
    }
}
