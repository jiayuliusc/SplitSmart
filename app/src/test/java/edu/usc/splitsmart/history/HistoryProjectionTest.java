package edu.usc.splitsmart.history;

import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Test;
import edu.usc.splitsmart.domain.calculation.HistoryProjection;
import edu.usc.splitsmart.domain.model.*;

public final class HistoryProjectionTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    // ------------------------------------------------------------------ fixtures

    private static Expense expense(String id, String title, long cents, Category category, String note,
                                   LocalDate date, Instant createdAt, String... participants) {
        return new Expense(id, "g", title, cents, "USD", "A", "A", List.of(participants),
            SplitMethod.EQUAL, category, note, date, createdAt, createdAt, List.of());
    }
    private static Expense expense(String id, String title, long cents, Category category, Instant createdAt) {
        return expense(id, title, cents, category, null, NOV_3, createdAt, "A", "B", "C");
    }
    private static Settlement settlement(String id, String payer, String payee, long cents, String note, Instant createdAt) {
        return new Settlement(id, "g", payer, payer, payee, cents, "USD", note, NOV_3, createdAt, createdAt);
    }
    private static LedgerSnapshot ledger(List<Expense> expenses, List<Settlement> settlements) {
        return new LedgerSnapshot("g", 7, List.of("A", "B", "C"), expenses, settlements);
    }
    private static HistoryFilter filter(String keyword, TransactionType type, Category category, String member, HistorySort sort) {
        return new HistoryFilter(keyword, type, category, member, sort);
    }
    private static HistoryFilter all(HistorySort sort) { return filter("", null, null, null, sort); }
    private static Instant t(long seconds) { return NOW.plusSeconds(seconds); }
    private static List<String> ids(List<HistoryItem> items) {
        return items.stream().map(i -> i.type().name() + ":" + i.recordId()).collect(Collectors.toList());
    }

    /** Dinner (FOOD, t1), Uber (TRANSPORTATION, t2, note "airport"), settlement B→A (t3, note "venmo"). */
    private static List<HistoryItem> sample() {
        return HistoryProjection.items(ledger(
            List.of(expense("e1", "Dinner", 3000, Category.FOOD, t(1)),
                expense("e2", "Uber", 1200, Category.TRANSPORTATION, "airport", NOV_3, t(2), "A", "C")),
            List.of(settlement("s1", "B", "A", 400, "venmo", t(3)))));
    }

    // ------------------------------------------------------------------ item shape

    @Test public void expenseBecomesHistoryItemWithSortedUniqueParticipants() {
        var e = expense("e", "Dinner", 3000, Category.FOOD, "tip incl", NOV_3, t(1), "C", "A", "B");
        HistoryItem item = HistoryProjection.items(ledger(List.of(e), List.of())).get(0);
        assertEquals(TransactionType.EXPENSE, item.type());
        assertEquals("e", item.recordId());
        assertEquals("Dinner", item.title());
        assertEquals(3000, item.amountCents());
        assertEquals("A", item.payerId());
        assertNull(item.payeeId());
        assertEquals(Category.FOOD, item.category());
        assertEquals("tip incl", item.note());
        assertEquals(List.of("A", "B", "C"), item.involvedMemberIds());
        assertEquals(NOV_3, item.localDate());
    }

    @Test public void settlementBecomesLabeledItemWithoutCategory() {
        HistoryItem item = HistoryProjection.items(ledger(List.of(), List.of(settlement("s", "C", "A", 400, null, t(1))))).get(0);
        assertEquals(TransactionType.SETTLEMENT, item.type());
        assertEquals("Settlement", item.title());
        assertEquals(400, item.amountCents());
        assertEquals("C", item.payerId());
        assertEquals("A", item.payeeId());
        assertNull(item.category());
        assertEquals(List.of("A", "C"), item.involvedMemberIds());
    }

    @Test public void itemsIncludeEveryExpenseAndSettlement() {
        assertEquals(3, sample().size());
        assertTrue(HistoryProjection.items(ledger(List.of(), List.of())).isEmpty());
    }

    // ------------------------------------------------------------------ sorting

    @Test public void emptyFilterKeepsEverythingNewestFirst() {
        assertEquals(List.of("SETTLEMENT:s1", "EXPENSE:e2", "EXPENSE:e1"),
            ids(HistoryProjection.apply(sample(), all(HistorySort.NEWEST_FIRST))));
    }

    @Test public void oldestFirstIsExactReverseOfNewestFirst() {
        List<String> oldest = ids(HistoryProjection.apply(sample(), all(HistorySort.OLDEST_FIRST)));
        List<String> newest = new ArrayList<>(ids(HistoryProjection.apply(sample(), all(HistorySort.NEWEST_FIRST))));
        Collections.reverse(newest);
        assertEquals(oldest, newest);
        assertEquals(List.of("EXPENSE:e1", "EXPENSE:e2", "SETTLEMENT:s1"), oldest);
    }

    @Test public void tiesBreakByTypeThenRecordIdEvenWhenIdsCollide() {
        var items = HistoryProjection.items(ledger(
            List.of(expense("x", "Lunch", 100, Category.FOOD, t(5)), expense("a", "Snacks", 100, Category.FOOD, t(5))),
            List.of(settlement("x", "B", "A", 50, null, t(5)))));
        assertEquals(List.of("EXPENSE:a", "EXPENSE:x", "SETTLEMENT:x"),
            ids(HistoryProjection.apply(items, all(HistorySort.OLDEST_FIRST))));
        assertEquals(List.of("SETTLEMENT:x", "EXPENSE:x", "EXPENSE:a"),
            ids(HistoryProjection.apply(items, all(HistorySort.NEWEST_FIRST))));
    }

    // ------------------------------------------------------------------ search and filters

    @Test public void keywordMatchesTitleOrNoteIgnoringCase() {
        assertEquals(List.of("EXPENSE:e1"), ids(HistoryProjection.apply(sample(), filter("dIN", null, null, null, HistorySort.NEWEST_FIRST))));
        assertEquals(List.of("EXPENSE:e2"), ids(HistoryProjection.apply(sample(), filter("AIRPORT", null, null, null, HistorySort.NEWEST_FIRST))));
        assertEquals(List.of("SETTLEMENT:s1"), ids(HistoryProjection.apply(sample(), filter("venmo", null, null, null, HistorySort.NEWEST_FIRST))));
        assertTrue(HistoryProjection.apply(sample(), filter("zzz", null, null, null, HistorySort.NEWEST_FIRST)).isEmpty());
    }

    @Test public void keywordSearchToleratesMissingNotes() {
        // e1 has a null note; matching "uber" must not throw and must only return e2.
        assertEquals(List.of("EXPENSE:e2"), ids(HistoryProjection.apply(sample(), filter("uber", null, null, null, HistorySort.NEWEST_FIRST))));
    }

    @Test public void typeFilterKeepsOnlyThatType() {
        assertEquals(List.of("EXPENSE:e2", "EXPENSE:e1"),
            ids(HistoryProjection.apply(sample(), filter("", TransactionType.EXPENSE, null, null, HistorySort.NEWEST_FIRST))));
        assertEquals(List.of("SETTLEMENT:s1"),
            ids(HistoryProjection.apply(sample(), filter("", TransactionType.SETTLEMENT, null, null, HistorySort.NEWEST_FIRST))));
    }

    @Test public void categoryFilterKeepsMatchingExpensesAndExcludesSettlements() {
        assertEquals(List.of("EXPENSE:e1"),
            ids(HistoryProjection.apply(sample(), filter("", null, Category.FOOD, null, HistorySort.NEWEST_FIRST))));
        assertTrue(HistoryProjection.apply(sample(), filter("", null, Category.OTHER, null, HistorySort.NEWEST_FIRST)).isEmpty());
    }

    @Test public void memberFilterKeepsTransactionsInvolvingThatMember() {
        // B is a participant in e1 and the payer of s1, but not in e2 (A, C).
        assertEquals(List.of("SETTLEMENT:s1", "EXPENSE:e1"),
            ids(HistoryProjection.apply(sample(), filter("", null, null, "B", HistorySort.NEWEST_FIRST))));
    }

    @Test public void activeFiltersCombineWithAnd() {
        assertEquals(List.of("EXPENSE:e1"),
            ids(HistoryProjection.apply(sample(), filter("", TransactionType.EXPENSE, null, "B", HistorySort.NEWEST_FIRST))));
        assertTrue(HistoryProjection.apply(sample(), filter("dinner", TransactionType.SETTLEMENT, null, null, HistorySort.NEWEST_FIRST)).isEmpty());
    }

    @Test public void filteredResultIsUnmodifiable() {
        List<HistoryItem> result = HistoryProjection.apply(sample(), all(HistorySort.NEWEST_FIRST));
        assertThrows(UnsupportedOperationException.class, () -> result.remove(0));
    }

    // ------------------------------------------------------------------ monthly summary

    @Test public void contractFixtureExpenseCountsAndSettlementIsExcluded() {
        // CONTRACTS.md: 3000-cent FOOD expense plus 400-cent settlement → count 1, total 3000, FOOD 3000, others zero.
        MonthlySummary s = HistoryProjection.summary(
            ledger(List.of(expense("e", "Lunch", 3000, Category.FOOD, t(1))), List.of(settlement("s", "B", "A", 400, null, t(2)))),
            NOVEMBER);
        assertEquals(1, s.expenseCount());
        assertEquals(3000, s.totalCents());
        assertEquals(Long.valueOf(3000), s.categoryTotalsCents().get(Category.FOOD));
        assertEquals(Long.valueOf(0), s.categoryTotalsCents().get(Category.HOUSING));
        assertEquals(Long.valueOf(0), s.categoryTotalsCents().get(Category.TRANSPORTATION));
        assertEquals(Long.valueOf(0), s.categoryTotalsCents().get(Category.ENTERTAINMENT));
        assertEquals(Long.valueOf(0), s.categoryTotalsCents().get(Category.OTHER));
        assertEquals("g", s.groupId());
        assertEquals(NOVEMBER, s.month());
        assertEquals(7, s.revision());
    }

    @Test public void categoryTotalsAddUpToMonthTotal() {
        MonthlySummary s = HistoryProjection.summary(ledger(List.of(
            expense("a", "Dinner", 3000, Category.FOOD, t(1)),
            expense("b", "Groceries", 1550, Category.FOOD, t(2)),
            expense("c", "Rent", 90000, Category.HOUSING, t(3)),
            expense("d", "Movie", 1234, Category.ENTERTAINMENT, t(4))), List.of()), NOVEMBER);
        assertEquals(4, s.expenseCount());
        assertEquals(95784, s.totalCents());
        assertEquals(Long.valueOf(4550), s.categoryTotalsCents().get(Category.FOOD));
        long sum = s.categoryTotalsCents().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(s.totalCents(), sum);
    }

    @Test public void monthUsesLocalDateNotCreatedAtTimestamp() {
        // Created Oct 31 at 11 pm Pacific: already Nov 1 in UTC, but it belongs to October.
        var lateOctober = expense("e", "Halloween", 2000, Category.ENTERTAINMENT, null,
            LocalDate.of(2026, 10, 31), Instant.parse("2026-11-01T06:00:00Z"), "A", "B");
        var l = ledger(List.of(lateOctober), List.of());
        assertEquals(1, HistoryProjection.summary(l, YearMonth.of(2026, 10)).expenseCount());
        assertEquals(0, HistoryProjection.summary(l, NOVEMBER).expenseCount());
    }

    @Test public void emptyMonthHasZeroCountAndAllFiveZeroCategories() {
        MonthlySummary s = HistoryProjection.summary(
            ledger(List.of(expense("e", "Lunch", 3000, Category.FOOD, t(1))), List.of(settlement("s", "B", "A", 400, null, t(2)))),
            YearMonth.of(2026, 9));
        assertEquals(0, s.expenseCount());
        assertEquals(0, s.totalCents());
        assertEquals(EnumSet.allOf(Category.class), s.categoryTotalsCents().keySet());
        for (Long cents : s.categoryTotalsCents().values()) assertEquals(Long.valueOf(0), cents);
    }

    @Test public void overflowingTotalThrowsArithmeticException() {
        var l = ledger(List.of(
            expense("a", "Huge", Long.MAX_VALUE, Category.OTHER, t(1)),
            expense("b", "One more", 1, Category.OTHER, t(2))), List.of());
        assertThrows(ArithmeticException.class, () -> HistoryProjection.summary(l, NOVEMBER));
    }
}
