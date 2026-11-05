package edu.usc.splitsmart.data.firebase;

import java.time.LocalDate;
import java.util.*;
import com.google.firebase.firestore.DocumentSnapshot;
import edu.usc.splitsmart.domain.model.*;

final class ExpenseCodec {
    private ExpenseCodec() { }

    private static long cents(Map<?, ?> data, boolean allowZero) {
        String text = IdentityCodec.string(data, "amountCents");
        if (!text.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Invalid money");
        long amount = Long.parseLong(text);
        if (amount < (allowZero ? 0 : 1) || amount > 9007199254740991L)
            throw new IllegalArgumentException("Money outside supported range");
        return amount;
    }

    static ExpenseShare share(Object value) {
        Map<?, ?> data = IdentityCodec.map(value);
        String pId = IdentityCodec.string(data, "participantId");
        long cents = cents(data, true);
        Integer bp = data.containsKey("basisPoints") ? (int) IdentityCodec.integer(data.get("basisPoints")) : null;
        return new ExpenseShare(pId, cents, bp);
    }

    static Expense expense(Object value) {
        Map<?, ?> data = IdentityCodec.map(value);
        String expenseId = IdentityCodec.string(data, "expenseId");
        String groupId = IdentityCodec.string(data, "groupId");
        String title = IdentityCodec.string(data, "title");
        long cents = cents(data, false);
        String currency = IdentityCodec.string(data, "currency");
        String creatorId = IdentityCodec.string(data, "creatorId");
        String payerId = IdentityCodec.string(data, "payerId");
        List<String> parts = IdentityCodec.ids(data.get("participantIds"));
        SplitMethod method = SplitMethod.valueOf(IdentityCodec.string(data, "splitMethod"));
        Category cat = Category.valueOf(IdentityCodec.string(data, "category"));
        String note = IdentityCodec.optional(data, "note");
        LocalDate date = LocalDate.parse(IdentityCodec.string(data, "localDate"));
        var createdAt = IdentityCodec.time(data.get("createdAt"));
        var updatedAt = IdentityCodec.time(data.get("updatedAt"));

        List<?> rawShares = (List<?>) data.get("shares");
        List<ExpenseShare> shares = new ArrayList<>();
        if (rawShares != null) {
            for (Object s : rawShares) {
                shares.add(share(s));
            }
        }
        return new Expense(expenseId, groupId, title, cents, currency, creatorId, payerId, parts, method, cat, note, date, createdAt, updatedAt, shares);
    }

    static Expense expense(DocumentSnapshot doc, String groupId) {
        Map<String, Object> data = new HashMap<>(Objects.requireNonNull(doc.getData()));
        IdentityCodec.version(data);
        data.put("expenseId", doc.getId());
        data.put("groupId", groupId);
        if (data.get("amountCents") instanceof Number num) {
            data.put("amountCents", Long.toString(num.longValue()));
        }
        return expense(data);
    }

    static LedgerSnapshot ledger(Object value) {
        Map<?, ?> data = IdentityCodec.map(value);
        IdentityCodec.version(data);
        Map<?, ?> ledgerData = IdentityCodec.map(data.get("ledger"));
        String groupId = IdentityCodec.string(ledgerData, "groupId");
        long revision = IdentityCodec.revision(IdentityCodec.string(ledgerData, "revision"));
        List<String> memberIds = IdentityCodec.ids(ledgerData.get("memberIds"));

        if (!(ledgerData.get("expenses") instanceof List<?> rawExpenses) ||
                !(ledgerData.get("settlements") instanceof List<?> rawSettlements)) {
            throw new IllegalArgumentException("Invalid ledger financial arrays");
        }
        List<Expense> expenses = new ArrayList<>();
        for (Object e : rawExpenses) expenses.add(expense(e));
        List<Settlement> settlements = new ArrayList<>();
        for (Object s : rawSettlements) settlements.add(settlement(s));
        return new LedgerSnapshot(groupId, revision, memberIds, expenses, settlements);
    }

    static Settlement settlement(Object value) {
        Map<?, ?> data = IdentityCodec.map(value);
        Set<String> allowed = Set.of("settlementId", "groupId", "creatorId", "payerId", "payeeId",
                "amountCents", "currency", "note", "localDate", "createdAt", "updatedAt");
        if (!allowed.containsAll(data.keySet())) throw new IllegalArgumentException("Unknown settlement field");
        return new Settlement(IdentityCodec.string(data, "settlementId"), IdentityCodec.string(data, "groupId"),
                IdentityCodec.string(data, "creatorId"), IdentityCodec.string(data, "payerId"),
                IdentityCodec.string(data, "payeeId"), cents(data, false),
                IdentityCodec.string(data, "currency"), IdentityCodec.optional(data, "note"),
                LocalDate.parse(IdentityCodec.string(data, "localDate")),
                IdentityCodec.time(data.get("createdAt")), IdentityCodec.time(data.get("updatedAt")));
    }

    static Map<String, Object> wireSplitInput(SplitInput input) {
        Map<String, Object> map = new HashMap<>();
        map.put("participantId", input.participantId());
        if (input.amountCents() != null) {
            map.put("amountCents", Long.toString(input.amountCents()));
        }
        if (input.basisPoints() != null) {
            map.put("basisPoints", input.basisPoints());
        }
        return map;
    }

    static Map<String, Object> wireDraft(ExpenseDraft draft) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", draft.title());
        map.put("amountCents", Long.toString(draft.amountCents()));
        map.put("payerId", draft.payerId());
        map.put("participantIds", draft.participantIds());
        map.put("splitMethod", draft.splitMethod().name());
        map.put("category", draft.category().name());
        if (draft.note() != null) {
            map.put("note", draft.note());
        }
        List<Map<String, Object>> inputs = new ArrayList<>();
        for (SplitInput in : draft.splitInputs()) {
            inputs.add(wireSplitInput(in));
        }
        map.put("splitInputs", inputs);
        return map;
    }
}
