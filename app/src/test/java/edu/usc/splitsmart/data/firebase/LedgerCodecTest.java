package edu.usc.splitsmart.data.firebase;

import org.junit.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

public class LedgerCodecTest {
    private Map<String, Object> ledger() {
        return new HashMap<>(Map.of("groupId", "group", "revision", "7", "memberIds", List.of("payer", "payee"),
                "expenses", List.of(), "settlements", List.of()));
    }
    private Object response(Map<String, Object> ledger) { return Map.of("schemaVersion", 1, "ledger", ledger); }

    @Test public void decodesCompleteSettlement() {
        var settlement = new HashMap<String, Object>(Map.of(
                "settlementId", "s1", "groupId", "group", "creatorId", "payer", "payerId", "payer",
                "payeeId", "payee", "amountCents", "123", "currency", "USD", "note", "Paid back",
                "localDate", "2026-11-03", "createdAt", Map.of("seconds", 123L, "nanoseconds", 1000)));
        settlement.put("updatedAt", Map.of("seconds", 124L, "nanoseconds", 0));
        var data = ledger(); data.put("settlements", List.of(settlement));
        var decoded = ExpenseCodec.ledger(response(data));
        assertEquals(7, decoded.revision()); assertEquals(1, decoded.settlements().size());
        var value = decoded.settlements().get(0);
        assertEquals("s1", value.settlementId()); assertEquals("group", value.groupId());
        assertEquals("payer", value.creatorId()); assertEquals("payer", value.payerId()); assertEquals("payee", value.payeeId());
        assertEquals(123, value.amountCents()); assertEquals("USD", value.currency()); assertEquals("Paid back", value.note());
        assertEquals(LocalDate.of(2026, 11, 3), value.localDate());
        assertEquals(Instant.ofEpochSecond(123, 1000), value.createdAt()); assertEquals(Instant.ofEpochSecond(124), value.updatedAt());
        settlement.put("unknown", "bad");
        assertThrows(IllegalArgumentException.class, () -> ExpenseCodec.ledger(response(data)));
        settlement.remove("unknown");
        settlement.remove("note");
        assertNull(ExpenseCodec.ledger(response(data)).settlements().get(0).note());
    }

    @Test public void rejectsMissingOrMalformedFinancialArrays() {
        for (String field : List.of("expenses", "settlements")) {
            var data = ledger(); data.remove(field);
            assertThrows(IllegalArgumentException.class, () -> ExpenseCodec.ledger(response(data)));
            data.put(field, "invalid");
            assertThrows(IllegalArgumentException.class, () -> ExpenseCodec.ledger(response(data)));
            data.put(field, List.of("invalid"));
            assertThrows(IllegalArgumentException.class, () -> ExpenseCodec.ledger(response(data)));
        }
    }

    @Test public void rejectsNoncanonicalOrOutOfRangeMoney() {
        for (String amount : List.of("+123", "00123", "-1", "0", "9007199254740992")) {
            var data = ledger();
            data.put("settlements", List.of(Map.of("settlementId", "s", "groupId", "g", "creatorId", "a",
                    "payerId", "a", "payeeId", "b", "amountCents", amount, "currency", "USD",
                    "localDate", "2026-11-03", "createdAt", Map.of("seconds", 1, "nanoseconds", 0),
                    "updatedAt", Map.of("seconds", 1, "nanoseconds", 0))));
            assertThrows(IllegalArgumentException.class, () -> ExpenseCodec.ledger(response(data)));
        }
        for (String amount : List.of("+123", "00123", "-1", "9007199254740992")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ExpenseCodec.share(Map.of("participantId", "a", "amountCents", amount)));
        }
        assertEquals(0, ExpenseCodec.share(Map.of("participantId", "a", "amountCents", "0")).amountCents());
    }

    @Test public void rejectsUnsupportedResponseVersion() {
        assertThrows(IllegalArgumentException.class, () -> ExpenseCodec.ledger(Map.of("schemaVersion", 2, "ledger", ledger())));
    }
}
