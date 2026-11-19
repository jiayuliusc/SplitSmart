package edu.usc.splitsmart.presentation.history;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.expenses.ExpenseDetailScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

/**
 * Binds the history section embedded in the group page. Kept in the history package so the group
 * screen only needs a few lines to host it.
 */
public final class HistorySection {
    private final AppCompatActivity activity;
    private final HistoryViewModel model;
    private final String groupId;
    private final Consumer<Intent> open;

    private final LinearLayout rows;
    private final TextView empty;
    private final EditText keyword;
    private final Spinner type;
    private final Spinner category;
    private final Spinner member;
    private final Button sort;

    private Map<String, String> names = Map.of();
    private final List<String> memberIds = new ArrayList<>();

    /** @param open launches a detail screen; pass the group page's result launcher so returning refreshes. */
    public HistorySection(AppCompatActivity activity, HistoryViewModel model, String groupId, Consumer<Intent> open) {
        this.activity = activity; this.model = model; this.groupId = groupId; this.open = open;
        rows = activity.findViewById(R.id.history_rows);
        empty = activity.findViewById(R.id.history_empty);
        keyword = activity.findViewById(R.id.history_keyword);
        type = activity.findViewById(R.id.history_type);
        category = activity.findViewById(R.id.history_category);
        member = activity.findViewById(R.id.history_member);
        sort = activity.findViewById(R.id.history_sort);

        HistoryFilter filter = model.filter();

        // Keyword: applied on the Search button or the keyboard's search key, not on every keystroke.
        keyword.setText(filter.keyword());
        activity.findViewById(R.id.history_search).setOnClickListener(v -> model.setKeyword(keyword.getText().toString()));
        keyword.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            model.setKeyword(keyword.getText().toString());
            return true;
        });

        // Type: All / Expenses / Settlements.
        type.setAdapter(adapter(List.of(s(R.string.history_all_types), s(R.string.history_type_expenses), s(R.string.history_type_settlements))));
        type.setSelection(filter.type() == null ? 0 : filter.type() == TransactionType.EXPENSE ? 1 : 2);
        type.setOnItemSelectedListener(listener(position -> {
            TransactionType chosen = position == 0 ? null : position == 1 ? TransactionType.EXPENSE : TransactionType.SETTLEMENT;
            model.setType(chosen);
            syncCategory();
        }));

        // Category: All + the five categories. Disabled while "Settlements" is selected.
        List<String> categoryLabels = new ArrayList<>();
        categoryLabels.add(s(R.string.history_all_categories));
        for (Category c : Category.values()) categoryLabels.add(label(c));
        category.setAdapter(adapter(categoryLabels));
        category.setOnItemSelectedListener(listener(position ->
            model.setCategory(position == 0 ? null : Category.values()[position - 1])));
        syncCategory();

        // Member: filled once the group's members load (see setMembers).
        setMembers(List.of());
        member.setOnItemSelectedListener(listener(position ->
            model.setMember(position < memberIds.size() ? memberIds.get(position) : null)));

        // Sort toggle.
        sortLabel();
        sort.setOnClickListener(v -> { model.toggleSort(); sortLabel(); });

        model.items.observe(activity, this::render);
    }

    /** Call from the group page's members observer: supplies names for rows and the member filter. */
    public void setMembers(List<MemberProfile> members) {
        Map<String, String> map = new HashMap<>();
        for (MemberProfile m : members) map.put(m.uid(), m.name());
        names = Map.copyOf(map);

        List<MemberProfile> byName = new ArrayList<>(members);
        byName.sort(Comparator.comparing(MemberProfile::name, String.CASE_INSENSITIVE_ORDER));
        memberIds.clear(); memberIds.add(null);
        List<String> labels = new ArrayList<>(); labels.add(s(R.string.history_all_members));
        for (MemberProfile m : byName) { memberIds.add(m.uid()); labels.add(m.name()); }
        member.setAdapter(adapter(labels));
        int selected = memberIds.indexOf(model.filter().memberId());
        member.setSelection(Math.max(selected, 0));

        render(model.items.getValue());   // re-draw rows with the new names
    }

    private void render(List<HistoryItem> items) {
        rows.removeAllViews();
        empty.setVisibility(items != null && items.isEmpty() ? View.VISIBLE : View.GONE);
        if (items == null) return;
        for (HistoryItem item : items) {
            View row = activity.getLayoutInflater().inflate(R.layout.row_history, rows, false);
            boolean expense = item.type() == TransactionType.EXPENSE;
            ((TextView) row.findViewById(R.id.row_history_type)).setText(expense
                ? activity.getString(R.string.history_row_expense, label(item.category()))
                : s(R.string.history_row_settlement));
            ((TextView) row.findViewById(R.id.row_history_title)).setText(item.title());
            ((TextView) row.findViewById(R.id.row_history_detail)).setText(activity.getString(
                R.string.history_row_detail, item.localDate().toString(), involved(item)));
            ((TextView) row.findViewById(R.id.row_history_amount)).setText(money(item.amountCents()));
            row.setOnClickListener(v -> open.accept(detailIntent(item)));
            rows.addView(row);
        }
    }

    private Intent detailIntent(HistoryItem item) {
        Intent intent = item.type() == TransactionType.EXPENSE
            ? new Intent(activity, ExpenseDetailScreen.class).putExtra(NavigationContract.EXPENSE_ID, item.recordId())
            : new Intent(activity, SettlementDetailScreen.class).putExtra(NavigationContract.SETTLEMENT_ID, item.recordId());
        return intent.putExtra(NavigationContract.GROUP_ID, groupId);
    }

    private String involved(HistoryItem item) {
        List<String> people = new ArrayList<>();
        for (String uid : item.involvedMemberIds()) people.add(names.getOrDefault(uid, s(R.string.balances_member)));
        return TextUtils.join(", ", people);
    }

    private void syncCategory() {
        boolean settlementsOnly = model.filter().type() == TransactionType.SETTLEMENT;
        category.setEnabled(!settlementsOnly);
        Category current = model.filter().category();
        category.setSelection(current == null ? 0 : current.ordinal() + 1);
    }

    private void sortLabel() {
        sort.setText(model.sort() == HistorySort.NEWEST_FIRST ? R.string.history_sort_newest : R.string.history_sort_oldest);
    }

    // ---------------------------------------------------------------- small helpers

    private ArrayAdapter<String> adapter(List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private static AdapterView.OnItemSelectedListener listener(Consumer<Integer> onSelect) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { onSelect.accept(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
    }

    private String s(int id) { return activity.getString(id); }

    /** FOOD -> "Food". */
    private static String label(Category category) {
        String name = category.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String money(long cents) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2));
    }
}
