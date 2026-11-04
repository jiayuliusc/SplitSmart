package edu.usc.splitsmart.presentation.settlements;
import org.junit.Test;
import java.time.LocalDate;
import edu.usc.splitsmart.domain.model.SettlementDraft;
import static org.junit.Assert.*;
public class SettlementIntentTest {
    @Test public void createRoundTripRetainsExactPayloadAcrossMidnight() {
        SettlementIntent intent = new SettlementIntent("create", 7, null, LocalDate.of(2026,11,4),new SettlementDraft("a",1234,"Thanks"));
        assertEquals(intent, SettlementIntent.restore(intent.save()));
    }
    @Test public void updateAndDeleteKeepOriginalRevisionAndRecord() {
        for (String operation : new String[]{"update", "delete"}) {
            SettlementIntent intent = new SettlementIntent(operation, 8, "s", null,
                "delete".equals(operation) ? null : new SettlementDraft("a",100,null));
            assertEquals(intent, SettlementIntent.restore(intent.save()));
        }
    }
}
