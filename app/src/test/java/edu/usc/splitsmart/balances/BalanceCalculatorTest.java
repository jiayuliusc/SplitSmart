package edu.usc.splitsmart.balances;

import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
import org.junit.Test;
import edu.usc.splitsmart.domain.calculation.BalanceCalculator;
import edu.usc.splitsmart.domain.contract.ContractVersion;
import edu.usc.splitsmart.domain.model.*;

public final class BalanceCalculatorTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static Expense expense(String id, String payer, long a, long b, long c) {
        return new Expense(id, "g", "Dinner", a+b+c, "USD", "C", payer, List.of("A","B","C"),
            SplitMethod.EXACT, Category.FOOD, null, LocalDate.of(2026,11,3), NOW, NOW,
            List.of(new ExpenseShare("A",a,null),new ExpenseShare("B",b,null),new ExpenseShare("C",c,null)));
    }
    private static Settlement settlement(String payer, String payee, long cents) {
        return new Settlement("s", "g", payer, payer, payee, cents, "USD", null, LocalDate.of(2026,11,3),NOW,NOW);
    }
    private static LedgerSnapshot ledger(List<Expense> expenses, List<Settlement> settlements) {
        return new LedgerSnapshot("g",7,List.of("C","B","A"),expenses,settlements);
    }
    private static long net(BalanceSummary summary, String other) {
        return summary.pairs().stream().filter(p -> p.otherUserId().equals(other)).findFirst().orElseThrow().netCents();
    }
    @Test public void canonicalExpenseAndSettlementUsePayerRatherThanCreator() {
        var ledger = ledger(List.of(expense("e","A",1000,1000,1000)),List.of(settlement("B","A",400)));
        var a = BalanceCalculator.calculate(ledger,"A");
        assertEquals(600,net(a,"B")); assertEquals(1000,net(a,"C"));
        assertEquals(1600,a.totalToReceiveCents()); assertEquals(0,a.totalToPayCents());
        assertEquals(7,a.revision()); assertEquals("g",a.groupId()); assertEquals("A",a.viewerId());
        var b = BalanceCalculator.calculate(ledger,"B");
        assertEquals(-600,net(b,"A")); assertEquals(0,net(b,"C")); assertEquals(600,b.totalToPayCents());
    }
    @Test public void oppositeExpensesNetOnlyWithinEachPair() {
        var l = ledger(List.of(expense("a","A",0,1000,0),expense("b","B",400,0,500),expense("c","C",200,0,0)),List.of());
        var a = BalanceCalculator.calculate(l,"A");
        assertEquals(600,net(a,"B")); assertEquals(-200,net(a,"C"));
        assertEquals(600,a.totalToReceiveCents()); assertEquals(200,a.totalToPayCents());
        assertEquals(500,net(BalanceCalculator.calculate(l,"B"),"C"));
    }
    @Test public void removingExpensePreservesSettlementAsSignedCredit() {
        var a = BalanceCalculator.calculate(ledger(List.of(),List.of(settlement("B","A",400))),"A");
        assertEquals(-400,net(a,"B")); assertEquals(400,a.totalToPayCents());
    }
    @Test public void zeroBalancesIncludeEveryOtherMemberInUidOrder() {
        var a = BalanceCalculator.calculate(ledger(List.of(),List.of()),"A");
        assertEquals(List.of(new PairBalance("B",0),new PairBalance("C",0)),a.pairs());
        var solo = BalanceCalculator.calculate(new LedgerSnapshot("g",1,List.of("A"),List.of(),List.of()),"A");
        assertTrue(solo.pairs().isEmpty()); assertEquals(0,solo.totalToReceiveCents());
    }
    @Test public void rangeUsesExactArithmeticAndRejectsUnrepresentableTotals() {
        long max = ContractVersion.MAX_ABSOLUTE_CENTS;
        var l = ledger(List.of(expense("e","A",0,max,0)),List.of());
        assertEquals(max,net(BalanceCalculator.calculate(l,"A"),"B"));
        var tooMuch = ledger(List.of(expense("e","A",0,max,0),expense("f","A",0,1,0)),List.of());
        assertThrows(ArithmeticException.class, () -> BalanceCalculator.calculate(tooMuch,"A"));
        var totalOverflow = ledger(List.of(expense("e","A",0,max,0),expense("f","A",0,0,1)),List.of());
        assertThrows(ArithmeticException.class, () -> BalanceCalculator.calculate(totalOverflow,"A"));
    }
    @Test public void invalidViewerScopeMembersAndSharesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BalanceCalculator.calculate(ledger(List.of(),List.of()),"outsider"));
        assertThrows(IllegalArgumentException.class, () -> BalanceCalculator.calculate(new LedgerSnapshot("g",1,List.of("A","A"),List.of(),List.of()),"A"));
        var e = expense("e","A",1000,1000,1000);
        var bad = new Expense("e","wrong",e.title(),e.amountCents(),e.currency(),e.creatorId(),e.payerId(),e.participantIds(),e.splitMethod(),e.category(),null,e.localDate(),NOW,NOW,e.shares());
        assertThrows(IllegalArgumentException.class, () -> BalanceCalculator.calculate(ledger(List.of(bad),List.of()),"A"));
        var missing = new Expense("e","g",e.title(),e.amountCents(),e.currency(),e.creatorId(),e.payerId(),e.participantIds(),e.splitMethod(),e.category(),null,e.localDate(),NOW,NOW,List.of());
        assertThrows(IllegalArgumentException.class, () -> BalanceCalculator.calculate(ledger(List.of(missing),List.of()),"A"));
    }
    @Test public void intermediateAggregatesCanExceedLongWithoutLosingCredits() {
        List<Expense> expenses=new ArrayList<>(); List<Settlement> settlements=new ArrayList<>();
        for(int i=0;i<1100;i++) {
            expenses.add(expense("e"+i,"A",0,ContractVersion.MAX_ABSOLUTE_CENTS,0));
            var payment=settlement("B","A",ContractVersion.MAX_ABSOLUTE_CENTS);
            settlements.add(new Settlement("s"+i,"g",payment.creatorId(),payment.payerId(),payment.payeeId(),payment.amountCents(),"USD",null,payment.localDate(),NOW,NOW));
        }
        var summary=BalanceCalculator.calculate(ledger(expenses,settlements),"A");
        assertEquals(0,summary.totalToPayCents()); assertEquals(0,summary.totalToReceiveCents());
    }
    @Test public void fiveHundredTransactionsRemainExact() {
        List<Expense> expenses = new ArrayList<>();
        for (int i=0;i<500;i++) expenses.add(expense("e"+i,"A",334,333,333));
        var a = BalanceCalculator.calculate(ledger(expenses,List.of()),"A");
        assertEquals(333000,a.totalToReceiveCents());
    }
}
