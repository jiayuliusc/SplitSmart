package edu.usc.splitsmart.presentation.expenses;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import java.util.*;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

public final class CreateExpenseScreen extends ProtectedScreen {
    private ExpenseViewModel model;
    private String expenseId;
    private boolean rendering;
    private final List<MemberProfile> memberProfiles = new ArrayList<>();
    private final List<CheckBox> participantBoxes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen(R.layout.screen_expense_create);
        String groupId = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status, getString(R.string.groups_missing_scope));
            show(R.id.access_retry, false);
            show(R.id.access_logout, false);
            return;
        }

        expenseId = getIntent().getStringExtra(NavigationContract.EXPENSE_ID);
        if (expenseId != null && !expenseId.matches("[A-Za-z0-9_-]{1,128}")) { finish(); return; }
        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new ExpenseViewModel(saved, services().expenses(), services().groups(), groupId))).get(ExpenseViewModel.class);

        bind(model, R.id.expense_submit);

        bindDraft(model, R.id.expense_title, "title");
        bindDraft(model, R.id.expense_amount, "amount");
        bindDraft(model, R.id.expense_note, "note");
        Spinner categorySpinner = findViewById(R.id.expense_category_spinner);
        ArrayAdapter<Category> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Category.values());
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(catAdapter);
        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!rendering) model.editDraft("category", Category.values()[position].name());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        ((Spinner) findViewById(R.id.expense_payer_spinner)).setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!rendering && position >= 0 && position < memberProfiles.size()) {
                    model.editDraft("payer", memberProfiles.get(position).uid());
                    lockControls();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        model.members.observe(this, members -> {
            memberProfiles.clear(); memberProfiles.addAll(members);
            renderDraft();
        });
        model.expense.observe(this, expense -> {
            if (expense != null && expenseId != null) { model.initializeEditor(expense); renderDraft(); }
        });
        model.busy.observe(this, ignored -> lockControls());
        if (expenseId != null) {
            text(R.id.feature_title, getString(R.string.expenses_edit_title));
            text(R.id.expense_submit, getString(R.string.expenses_save));
        }
        renderDraft();

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

        protect();
    }

    private void submit() {
        Spinner payerSpinner = findViewById(R.id.expense_payer_spinner);
        Spinner categorySpinner = findViewById(R.id.expense_category_spinner);

        int payerIdx = payerSpinner.getSelectedItemPosition();
        if (payerIdx < 0 || payerIdx >= memberProfiles.size()) return;
        String payerId = memberProfiles.get(payerIdx).uid();

        Category cat = (Category) categorySpinner.getSelectedItem();

        List<String> selectedParticipants = new ArrayList<>();
        for (CheckBox box : participantBoxes) {
            if (box.isChecked()) {
                selectedParticipants.add((String) box.getTag());
            }
        }

        ExpenseDraft original = model.originalDraft();
        if (expenseId != null && original != null && original.splitMethod() != SplitMethod.EQUAL) {
            selectedParticipants = original.participantIds();
        }
        if (expenseId == null) model.create(input(R.id.expense_title), input(R.id.expense_amount), payerId, selectedParticipants, cat, input(R.id.expense_note));
        else model.update(expenseId, input(R.id.expense_title), input(R.id.expense_amount), payerId, selectedParticipants, cat, input(R.id.expense_note));
    }

    private void renderDraft() {
        rendering = true;
        text(R.id.expense_title, model.draft("title", ""));
        text(R.id.expense_amount, model.draft("amount", ""));
        text(R.id.expense_note, model.draft("note", ""));
        Spinner category = findViewById(R.id.expense_category_spinner);
        category.setSelection(Category.valueOf(model.draft("category", Category.OTHER.name())).ordinal());
        Spinner payer = findViewById(R.id.expense_payer_spinner);
        List<String> names = new ArrayList<>();
        for (MemberProfile member : memberProfiles) names.add(member.name());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        payer.setAdapter(adapter);
        String payerId = model.draft("payer", currentUid == null ? "" : currentUid);
        for (int i = 0; i < memberProfiles.size(); i++) if (memberProfiles.get(i).uid().equals(payerId)) payer.setSelection(i);
        String defaultParticipants = String.join(",", memberProfiles.stream().map(MemberProfile::uid).collect(java.util.stream.Collectors.toList()));
        Set<String> selected = new HashSet<>(Arrays.asList(model.draft("participants", defaultParticipants).split(",")));
        LinearLayout container = findViewById(R.id.expense_participants_container);
        container.removeAllViews(); participantBoxes.clear();
        for (MemberProfile member : memberProfiles) {
            CheckBox box = new CheckBox(this);
            box.setText(member.name()); box.setTag(member.uid()); box.setChecked(selected.contains(member.uid()));
            box.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
            box.setOnCheckedChangeListener((button, checked) -> {
                if (rendering) return;
                List<String> ids = new ArrayList<>();
                for (CheckBox item : participantBoxes) if (item.isChecked()) ids.add((String) item.getTag());
                model.editDraft("participants", String.join(",", ids));
            });
            participantBoxes.add(box); container.addView(box);
        }
        ExpenseDraft original = model.originalDraft();
        if (original != null && original.splitMethod() != SplitMethod.EQUAL) {
            text(R.id.feature_intro, getString(original.splitMethod() == SplitMethod.EXACT
                ? R.string.expenses_exact_preserved : R.string.expenses_percentage_preserved));
        }
        rendering = false;
        lockControls();
    }
    private void lockControls() {
        boolean locked = model.isBusy() || model.pending();
        if (!model.isBusy() && model.ledger.getValue() == null) show(R.id.identity_retry, true);
        boolean ready = expenseId == null || model.originalDraft() != null;
        ExpenseDraft original = model.originalDraft();
        boolean nonEqual = original != null && original.splitMethod() != SplitMethod.EQUAL;
        findViewById(R.id.expense_payer_spinner).setEnabled(!locked && ready);
        findViewById(R.id.expense_category_spinner).setEnabled(!locked && ready);
        findViewById(R.id.expense_amount).setEnabled(!locked && ready && !(original != null && original.splitMethod() == SplitMethod.EXACT));
        Spinner payer = findViewById(R.id.expense_payer_spinner);
        int payerIndex = payer.getSelectedItemPosition();
        String payerId = payerIndex >= 0 && payerIndex < memberProfiles.size() ? memberProfiles.get(payerIndex).uid() : null;
        for (CheckBox box : participantBoxes) {
            boolean isPayer = box.getTag().equals(payerId);
            if (isPayer && !box.isChecked()) box.setChecked(true);
            box.setEnabled(!locked && ready && !nonEqual && !isPayer);
        }
        findViewById(R.id.expense_submit).setEnabled(!locked && ready && model.ledger.getValue() != null);
    }

    @Override
    protected void onAuthorized(User user) {
        if (model != null) {
            model.bindUser(user.uid());
            if (expenseId == null) model.loadGroup();
            else model.loadExpense(expenseId);
        }
    }

    @Override
    protected void onAccessLost() {
        if (model != null) {
            model.cancelOperations();
        }
    }
}
