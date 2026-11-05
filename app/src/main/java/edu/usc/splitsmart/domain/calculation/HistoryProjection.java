package edu.usc.splitsmart.domain.calculation;

import java.time.YearMonth;
import java.util.*;
import edu.usc.splitsmart.domain.model.*;

/** Read-only history and monthly-summary projections of one coherent ledger (SCHEMA.md "Drafts, projections and ordering"). */
public final class HistoryProjection {
    static final String SETTLEMENT_TITLE = "Settlement";

    /** OLDEST_FIRST order: (createdAt, type name, recordId). Type breaks ties because IDs can collide across collections. */
    private static final Comparator<HistoryItem> OLDEST_FIRST = Comparator
        .comparing(HistoryItem::createdAt)
        .thenComparing(item -> item.type().name())
        .thenComparing(HistoryItem::recordId);

    private HistoryProjection() { }

    // ---------------------------------------------------------------- history

    public static List<HistoryItem> items(LedgerSnapshot ledger) {
        List<HistoryItem> items = new ArrayList<>(ledger.expenses().size() + ledger.settlements().size());
        for (Expense expense : ledger.expenses()) items.add(fromExpense(expense));
        for (Settlement settlement : ledger.settlements()) items.add(fromSettlement(settlement));
        return items;
    }

    public static List<HistoryItem> apply(List<HistoryItem> items, HistoryFilter filter) {
        List<HistoryItem> result = new ArrayList<>();
        for (HistoryItem item : items) {
            if (matches(item, filter)) result.add(item);
        }
        result.sort(filter.sort() == HistorySort.NEWEST_FIRST ? OLDEST_FIRST.reversed() : OLDEST_FIRST);
        return List.copyOf(result);
    }

    // ---------------------------------------------------------------- monthly summary

    /** Expense count, total and per-category totals for one month. Settlements are never counted. */
    public static MonthlySummary summary(LedgerSnapshot ledger, YearMonth month) {
        Map<Category, Long> totals = new EnumMap<>(Category.class);
        for (Category category : Category.values()) totals.put(category, 0L);   // all five keys, even zero
        int count = 0;
        long total = 0;
        for (Expense expense : ledger.expenses()) {
            if (!YearMonth.from(expense.localDate()).equals(month)) continue;
            count++;
            total = Math.addExact(total, expense.amountCents());
            totals.put(expense.category(),
                Math.addExact(totals.get(expense.category()), expense.amountCents()));
        }
        return new MonthlySummary(ledger.groupId(), month, ledger.revision(), count, total, totals);
    }

    // ---------------------------------------------------------------- helpers

    private static HistoryItem fromExpense(Expense expense) {
        List<String> involved = sortedUnique(expense.participantIds());
        return new HistoryItem(
            expense.expenseId(), expense.groupId(), TransactionType.EXPENSE,
            expense.title(), expense.amountCents(), expense.currency(),
            expense.creatorId(), expense.payerId(),
            null,                                   // payeeId: absent for expenses
            involved, expense.category(), expense.note(),
            expense.localDate(), expense.createdAt(), expense.updatedAt());
    }

    private static HistoryItem fromSettlement(Settlement settlement) {
        List<String> involved = sortedUnique(List.of(settlement.payerId(), settlement.payeeId()));
        return new HistoryItem(
            settlement.settlementId(), settlement.groupId(), TransactionType.SETTLEMENT,
            SETTLEMENT_TITLE, settlement.amountCents(), settlement.currency(),
            settlement.creatorId(), settlement.payerId(), settlement.payeeId(),
            involved,
            null,                                   // category: absent for settlements
            settlement.note(),
            settlement.localDate(), settlement.createdAt(), settlement.updatedAt());
    }

    private static boolean matches(HistoryItem item, HistoryFilter filter) {
        String keyword = filter.keyword().toLowerCase(Locale.ROOT);
        if (!keyword.isEmpty()
            && !contains(item.title(), keyword)
            && !contains(item.note(), keyword)) return false;

        if (filter.type() != null && item.type() != filter.type()) return false;
        if (filter.category() != null && item.category() != filter.category()) return false;
        if (filter.memberId() != null && !item.involvedMemberIds().contains(filter.memberId())) return false;

        return true;
    }

    private static boolean contains(String text, String lowerKeyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private static List<String> sortedUnique(Collection<String> ids) {
        return List.copyOf(new TreeSet<>(ids));   // TreeSet = ASCII ascending and de-duplicated
    }
}
