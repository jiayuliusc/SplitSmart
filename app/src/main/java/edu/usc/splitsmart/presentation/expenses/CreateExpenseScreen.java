package edu.usc.splitsmart.presentation.expenses;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import java.math.BigDecimal;
import java.util.*;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

/** Expense editor with local, pre-save previews for equal, exact and percentage splits. */
public final class CreateExpenseScreen extends ProtectedScreen {
    private ExpenseViewModel model;
    private String expenseId;
    private final List<MemberProfile> memberProfiles = new ArrayList<>();
    private final List<CheckBox> participantBoxes = new ArrayList<>();
    private final Map<String, EditText> splitEditors = new HashMap<>();
    private boolean rendering;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen(R.layout.screen_expense_create);
        String groupId = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status, getString(R.string.groups_missing_scope));
            show(R.id.access_retry, false); show(R.id.access_logout, false); return;
        }
        expenseId = getIntent().getStringExtra(NavigationContract.EXPENSE_ID);
        if (expenseId != null && !expenseId.matches("[A-Za-z0-9_-]{1,128}")) { finish(); return; }

        expenseId = getIntent().getStringExtra(NavigationContract.EXPENSE_ID);
        if (expenseId != null && !expenseId.matches("[A-Za-z0-9_-]{1,128}")) { finish(); return; }
        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new ExpenseViewModel(saved, services().expenses(), services().groups(), groupId)))
            .get(ExpenseViewModel.class);
        bind(model, R.id.expense_submit);
        restoreTextDrafts();
        setupCategorySpinner();
        setupSplitMethodSpinner();
        watch(R.id.expense_title, this::requestPreview);
        watch(R.id.expense_amount, this::requestPreview);

        model.members.observe(this, this::renderMembers);
        model.expense.observe(this, expense -> {
            if (expense == null || expenseId == null) return;
            model.initializeEditor(expense);
            text(R.id.expense_title, model.draft("title", ""));
            text(R.id.expense_amount, model.draft("amount", ""));
            text(R.id.expense_note, model.draft("note", ""));
            setupCategorySpinner();
            setupSplitMethodSpinner();
            renderMembers(model.members.getValue() == null ? List.of() : model.members.getValue());
            if (expense.splitMethod() == SplitMethod.EXACT) findViewById(R.id.expense_amount).setEnabled(false);
        });
        model.splitPreview.observe(this, this::renderPreview);
        model.splitValidation.observe(this, message -> {
            String value = message == null ? "" : message;
            text(R.id.expense_split_error, value);
            show(R.id.expense_split_error, !value.isEmpty());
        });
        model.splitValid.observe(this, valid -> updateSubmitState());
        model.busy.observe(this, busy -> updateSubmitState());
        if (expenseId != null) {
            text(R.id.feature_title, getString(R.string.expenses_edit_title));
            text(R.id.expense_submit, getString(R.string.expenses_save));
        }

        findViewById(R.id.expense_submit).setOnClickListener(v -> submit());
        findViewById(R.id.identity_retry).setOnClickListener(v -> model.retry());
        model.created.observe(this, result -> {
            if (result == null) return;
            setResult(RESULT_OK, new Intent()
                .putExtra(NavigationContract.RESULT_REQUEST_ID, result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID, groupId)
                .putExtra(NavigationContract.RESULT_GROUP_REVISION, result.groupRevision().longValue()));
            finish();
        });
        updateSubmitState();
        protect();
    }

    private void restoreTextDrafts() {
        text(R.id.expense_title, model.draft("title", ""));
        text(R.id.expense_amount, model.draft("amount", ""));
        text(R.id.expense_note, model.draft("note", ""));
        bindDraft(model, R.id.expense_title, "title");
        bindDraft(model, R.id.expense_amount, "amount");
        bindDraft(model, R.id.expense_note, "note");
    }

    private void setupCategorySpinner() {
        Spinner spinner = findViewById(R.id.expense_category_spinner);
        ArrayAdapter<Category> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, Category.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        try { spinner.setSelection(Category.valueOf(model.draft("category", Category.values()[0].name())).ordinal()); }
        catch (IllegalArgumentException ignored) { spinner.setSelection(0); }
        spinner.setOnItemSelectedListener(changes(() -> {
            Category category = (Category) spinner.getSelectedItem();
            if (category != null) model.editDraft("category", category.name());
            requestPreview();
        }));
        spinner.setEnabled(expenseId == null);
    }

    private void setupSplitMethodSpinner() {
        Spinner spinner = findViewById(R.id.expense_split_method_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
            List.of(getString(R.string.split_equal), getString(R.string.split_exact),
                getString(R.string.split_percentage)));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        try { spinner.setSelection(SplitMethod.valueOf(model.draft("splitMethod", "EQUAL")).ordinal()); }
        catch (IllegalArgumentException ignored) { spinner.setSelection(0); }
        spinner.setOnItemSelectedListener(changes(() -> {
            model.editDraft("splitMethod", splitMethod().name());
            rebuildSplitEditors(); requestPreview();
        }));
    }

    private void renderMembers(List<MemberProfile> members) {
        rendering = true;
        try {
            memberProfiles.clear(); memberProfiles.addAll(members);
            renderPayers(members); renderParticipantChecks(members); rebuildSplitEditors();
        } finally { rendering = false; }
        requestPreview();
    }

    private void renderPayers(List<MemberProfile> members) {
        Spinner spinner = findViewById(R.id.expense_payer_spinner);
        List<String> names = new ArrayList<>();
        for (MemberProfile member : members) names.add(member.name());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String savedPayer = model.draft("payer", "");
        for (int i = 0; i < members.size(); i++) if (members.get(i).uid().equals(savedPayer)) spinner.setSelection(i);
        spinner.setOnItemSelectedListener(changes(() -> {
            String payer = payerId();
            if (payer != null) model.editDraft("payer", payer);
            syncPayerParticipant();
            requestPreview();
        }));
    }

    private void renderParticipantChecks(List<MemberProfile> members) {
        LinearLayout container = findViewById(R.id.expense_participants_container);
        container.removeAllViews(); participantBoxes.clear();
        String saved = model.draft("participants", "__ALL__");
        Set<String> selected = "__ALL__".equals(saved) ? null
            : "__NONE__".equals(saved) ? Set.of()
              : new HashSet<>(Arrays.asList(saved.split(",", -1)));
        for (MemberProfile member : members) {
            CheckBox box = new CheckBox(this);
            box.setText(member.name()); box.setTag(member.uid());
            box.setChecked(selected == null || selected.contains(member.uid()));
            ExpenseDraft original = model.originalDraft();
            box.setEnabled(expenseId == null || original == null || original.splitMethod() == SplitMethod.EQUAL);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (rendering) return;
                persistParticipants(); rebuildSplitEditors(); requestPreview();
            });
            participantBoxes.add(box); container.addView(box);
        }
        syncPayerParticipant();
        persistParticipants();
    }

    private void syncPayerParticipant() {
        String payer = payerId();
        ExpenseDraft original = model.originalDraft();
        boolean editable = expenseId == null || original == null || original.splitMethod() == SplitMethod.EQUAL;
        boolean changed = false;
        boolean wasRendering = rendering;
        rendering = true;
        for (CheckBox box : participantBoxes) {
            boolean isPayer = Objects.equals(box.getTag(), payer);
            if (isPayer && !box.isChecked()) {
                box.setChecked(true);
                changed = true;
            }
            box.setEnabled(editable && !isPayer);
        }
        rendering = wasRendering;
        if (changed && !rendering) {
            persistParticipants();
            rebuildSplitEditors();
        }
    }

    private void rebuildSplitEditors() {
        LinearLayout container = findViewById(R.id.expense_split_inputs_container);
        container.removeAllViews(); splitEditors.clear();
        SplitMethod method = splitMethod();
        show(R.id.expense_split_inputs_label, method != SplitMethod.EQUAL);
        show(R.id.expense_split_inputs_container, method != SplitMethod.EQUAL);
        if (method == SplitMethod.EQUAL) return;

        for (String participantId : selectedParticipantIds()) {
            MemberProfile member = member(participantId);
            String participantName = member == null ? participantId : member.name();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(participantName); name.setTextSize(16);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            EditText editor = new EditText(this);
            editor.setSingleLine(true);
            editor.setHint(method == SplitMethod.EXACT
                ? getString(R.string.split_amount_hint) : getString(R.string.split_percentage_hint));
            editor.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            String key = splitDraftKey(method, participantId);
            editor.setText(model.draft(key, ""));
            editor.setContentDescription(getString(R.string.split_input_content_description,
                participantName,
                method == SplitMethod.EXACT ? getString(R.string.split_exact) : getString(R.string.split_percentage)));
            editor.addTextChangedListener(new SimpleWatcher() {
                @Override public void afterTextChanged(Editable value) {
                    model.editDraft(key, value.toString()); requestPreview();
                }
            });
            editor.setEnabled(expenseId == null);
            row.addView(editor, new LinearLayout.LayoutParams(dp(132), LinearLayout.LayoutParams.WRAP_CONTENT));
            splitEditors.put(participantId, editor); container.addView(row);
        }
    }

    private void requestPreview() {
        if (rendering || model == null) return;
        try {
            model.previewSplit(input(R.id.expense_title), input(R.id.expense_amount), payerId(),
                selectedParticipantIds(), splitMethod(), splitInputs(),
                (Category) ((Spinner) findViewById(R.id.expense_category_spinner)).getSelectedItem(),
                input(R.id.expense_note));
        } catch (IllegalArgumentException error) { model.clearSplitPreview(error.getMessage()); }
    }

    private void submit() {
        if (!Boolean.TRUE.equals(model.splitValid.getValue())) { requestPreview(); return; }
        try {
            Category category = (Category) ((Spinner) findViewById(R.id.expense_category_spinner)).getSelectedItem();
            if (expenseId == null) {
                model.create(input(R.id.expense_title), input(R.id.expense_amount), payerId(),
                    selectedParticipantIds(), splitMethod(), splitInputs(), category, input(R.id.expense_note));
            } else {
                model.update(expenseId, input(R.id.expense_title), input(R.id.expense_amount), payerId(),
                    selectedParticipantIds(), category, input(R.id.expense_note));
            }
        } catch (IllegalArgumentException error) { model.clearSplitPreview(error.getMessage()); }
    }

    private List<SplitInput> splitInputs() {
        SplitMethod method = splitMethod();
        if (method == SplitMethod.EQUAL) return List.of();
        List<SplitInput> result = new ArrayList<>();
        for (String participantId : selectedParticipantIds()) {
            EditText editor = splitEditors.get(participantId);
            String raw = editor == null ? "" : editor.getText().toString();
            if (raw.trim().isEmpty()) throw new IllegalArgumentException(method == SplitMethod.EXACT
                ? getString(R.string.split_enter_all_amounts) : getString(R.string.split_enter_all_percentages));
            if (method == SplitMethod.EXACT) {
                try { result.add(new SplitInput(participantId, ExpenseViewModel.parseDollarsToCents(raw), null)); }
                catch (Exception error) { throw new IllegalArgumentException(getString(R.string.split_invalid_amount)); }
            } else {
                try { result.add(new SplitInput(participantId, null, ExpenseViewModel.parsePercentageToBasisPoints(raw))); }
                catch (Exception error) { throw new IllegalArgumentException(getString(R.string.split_invalid_percentage)); }
            }
        }
        return result;
    }

    private void renderPreview(List<ExpenseShare> shares) {
        LinearLayout container = findViewById(R.id.expense_split_preview_container);
        container.removeAllViews();
        for (ExpenseShare share : shares) {
            TextView row = new TextView(this);
            MemberProfile member = member(share.participantId());
            String name = member == null ? share.participantId() : member.name();
            String amount = getString(R.string.split_currency_amount,
                BigDecimal.valueOf(share.amountCents(), 2).toPlainString());
            String suffix = share.basisPoints() == null ? "" : getString(R.string.split_percentage_suffix,
                BigDecimal.valueOf(share.basisPoints(), 2).stripTrailingZeros().toPlainString());
            row.setText(getString(R.string.split_preview_row, name, amount, suffix));
            row.setTextSize(16); row.setPadding(0, dp(4), 0, dp(4));
            container.addView(row);
        }
        show(R.id.expense_split_preview_label, !shares.isEmpty());
        show(R.id.expense_split_preview_container, !shares.isEmpty());
        updateSubmitState();
    }

    private void updateSubmitState() {
        View submit = findViewById(R.id.expense_submit);
        if (submit != null && model != null) submit.setEnabled(Boolean.TRUE.equals(model.splitValid.getValue())
            && !model.isBusy() && !model.pending() && model.ledger.getValue() != null);
    }

    private SplitMethod splitMethod() {
        int position = ((Spinner) findViewById(R.id.expense_split_method_spinner)).getSelectedItemPosition();
        return position >= 0 && position < SplitMethod.values().length ? SplitMethod.values()[position] : SplitMethod.EQUAL;
    }

    private String payerId() {
        int position = ((Spinner) findViewById(R.id.expense_payer_spinner)).getSelectedItemPosition();
        return position >= 0 && position < memberProfiles.size() ? memberProfiles.get(position).uid() : null;
    }

    private List<String> selectedParticipantIds() {
        List<String> result = new ArrayList<>();
        for (CheckBox box : participantBoxes) if (box.isChecked()) result.add((String) box.getTag());
        return result;
    }

    private void persistParticipants() {
        if (model == null) return;
        List<String> selected = selectedParticipantIds();
        model.editDraft("participants", selected.isEmpty() ? "__NONE__" : String.join(",", selected));
    }
    private MemberProfile member(String id) {
        for (MemberProfile member : memberProfiles) if (member.uid().equals(id)) return member;
        return null;
    }
    private String splitDraftKey(SplitMethod method, String id) { return "split:" + method.name() + ":" + id; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void watch(int id, Runnable action) {
        ((EditText) findViewById(id)).addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable value) { action.run(); }
        });
    }

    private AdapterView.OnItemSelectedListener changes(Runnable action) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!rendering) action.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { if (!rendering) action.run(); }
        };
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
    }

    @Override protected void onAuthorized(User user) {
        if (model != null) {
            model.bindUser(user.uid());
            if (expenseId == null) model.loadGroup(); else model.loadExpense(expenseId);
        }
    }
    @Override protected void onAccessLost() { if (model != null) model.cancelOperations(); }
}
