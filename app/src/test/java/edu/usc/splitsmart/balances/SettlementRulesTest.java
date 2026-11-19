package edu.usc.splitsmart.balances;

import org.junit.Test;
import java.time.*;
import java.util.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.service.settlements.SettlementRules;
import static org.junit.Assert.*;

public class SettlementRulesTest {
    @Test public void exactMoneyRejectsFractionalCentsAndExponent() {
        assertEquals(1250, SettlementRules.cents("12.50"));
        for (String value : List.of("1.001", "1e2", "-1", "", "90071992547409.92")) {
            try { SettlementRules.cents(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void editPreviewRemovesOriginalPaymentAndPreservesOtherPayments() {
        Instant now = Instant.EPOCH;
        Expense expense = new Expense("e", "g", "Dinner", 2000, "USD", "a", "a", List.of("a", "b"), SplitMethod.EQUAL, Category.FOOD, null, LocalDate.now(), now, now, List.of(new ExpenseShare("a",1000,null),new ExpenseShare("b",1000,null)));
        Settlement original = new Settlement("s", "g", "b", "b", "a",400,"USD",null,LocalDate.now(),now,now);
        Settlement other = new Settlement("t", "g", "b", "b", "a",100,"USD",null,LocalDate.now(),now,now);
        LedgerSnapshot ledger = new LedgerSnapshot("g", 3, List.of("a","b"), List.of(expense),List.of(original, other));
        assertEquals(500, SettlementRules.remaining(ledger,"b","a",null));
        assertEquals(900, SettlementRules.remaining(ledger,"b","a","s"));
    }
    @Test public void invalidDraftHasFieldSpecificError() {
        assertEquals("amountCents", SettlementRules.validate(new SettlementDraft(null, "a",0,null)).field());
        assertEquals("payeeId", SettlementRules.validate(new SettlementDraft(null, "",1,null)).field());
        assertNull(SettlementRules.validate(new SettlementDraft(null, "a",100,null)));
    }
    @Test public void editPreviewAllowsTemporaryDebtAboveContractBound() {
        long max = edu.usc.splitsmart.domain.contract.ContractVersion.MAX_ABSOLUTE_CENTS;
        List<Expense> expenses = new ArrayList<>();
        for (String id : List.of("e1", "e2")) expenses.add(new Expense(id,"g","Dinner",max,"USD","a","a",List.of("a","b"),SplitMethod.EXACT,Category.FOOD,null,LocalDate.now(),Instant.EPOCH,Instant.EPOCH,
            List.of(new ExpenseShare("a",0,null),new ExpenseShare("b",max,null))));
        Settlement original = new Settlement("s","g","b","b","a",max,"USD",null,LocalDate.now(),Instant.EPOCH,Instant.EPOCH);
        LedgerSnapshot ledger = new LedgerSnapshot("g",3,List.of("a","b"),expenses,List.of(original));
        assertEquals(max, SettlementRules.remaining(ledger,"b","a",null));
        assertEquals(Math.multiplyExact(max,2), SettlementRules.remaining(ledger,"b","a","s"));
    }

}
