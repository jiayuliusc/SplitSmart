package edu.usc.splitsmart.presentation.expenses;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import java.util.*;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

public final class CreateExpenseScreen extends ProtectedScreen {
    private ExpenseViewModel model;
    private final List<MemberProfile> memberProfiles = new ArrayList<>();
    private final List<CheckBox> participantBoxes = new ArrayList<>();
    private String expenseId;
    private boolean isEditMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen(R.layout.screen_expense_create);
        String groupId = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        expenseId = getIntent().getStringExtra(NavigationContract.EXPENSE_ID);
        isEditMode = expenseId != null && expenseId.matches("[A-Za-z0-9_-]{1,128}");

        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status, getString(R.string.groups_missing_scope));
            show(R.id.access_retry, false);
            show(R.id.access_logout, false);
            return;
        }

        if (isEditMode) {
            text(R.id.feature_title, getString(R.string.expenses_edit_title));
            text(R.id.feature_intro, getString(R.string.expenses_edit_intro));
            ((Button) findViewById(R.id.expense_submit)).setText(R.string.expenses_save);
        }

        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new ExpenseViewModel(saved, services().expenses(), services().groups(), groupId))).get(ExpenseViewModel.class);

        bind(model, R.id.expense_submit);

        // Setup Category Spinner
        Spinner categorySpinner = findViewById(R.id.expense_category_spinner);
        ArrayAdapter<Category> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Category.values());
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(catAdapter);

        // Setup Payer Spinner Listener
        Spinner payerSpinner = findViewById(R.id.expense_payer_spinner);
        payerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePayerSelection(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        model.members.observe(this, members -> {
            memberProfiles.clear();
            memberProfiles.addAll(members);

            // Populate Payer Spinner
            List<String> names = new ArrayList<>();
            for (MemberProfile m : members) names.add(m.name());
            ArrayAdapter<String> payerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            payerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            payerSpinner.setAdapter(payerAdapter);

            // Populate Participants Checkboxes
            LinearLayout container = findViewById(R.id.expense_participants_container);
            container.removeAllViews();
            participantBoxes.clear();
            for (MemberProfile m : members) {
                CheckBox box = new CheckBox(this);
                box.setText(m.name());
                box.setTag(m.uid());
                box.setChecked(true);
                participantBoxes.add(box);
                container.addView(box);
            }

            if (isEditMode && model.expense.getValue() != null) {
                populateExpenseData(model.expense.getValue());
            } else {
                updatePayerSelection(payerSpinner.getSelectedItemPosition());
            }
        });

        if (isEditMode) {
            model.expense.observe(this, exp -> {
                if (exp != null && !memberProfiles.isEmpty()) {
                    populateExpenseData(exp);
                }
            });
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

        model.updated.observe(this, result -> {
            if (result == null) return;
            setResult(RESULT_OK, new Intent()
                .putExtra(NavigationContract.RESULT_REQUEST_ID, result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID, groupId)
                .putExtra(NavigationContract.RESULT_GROUP_REVISION, result.groupRevision().longValue()));
            finish();
        });

        protect();
    }

    private void updatePayerSelection(int payerIdx) {
        if (payerIdx < 0 || payerIdx >= memberProfiles.size()) return;
        String currentPayerUid = memberProfiles.get(payerIdx).uid();
        for (CheckBox box : participantBoxes) {
            String uid = (String) box.getTag();
            if (uid != null && uid.equals(currentPayerUid)) {
                box.setChecked(true);
                box.setEnabled(false);
            } else {
                box.setEnabled(true);
            }
        }
    }

    private void populateExpenseData(Expense exp) {
        EditText titleEdit = findViewById(R.id.expense_title);
        EditText amountEdit = findViewById(R.id.expense_amount);
        EditText noteEdit = findViewById(R.id.expense_note);

        titleEdit.setText(exp.title());
        amountEdit.setText(String.format(Locale.US, "%.2f", exp.amountCents() / 100.0));
        if (exp.note() != null) {
            noteEdit.setText(exp.note());
        }

        Spinner categorySpinner = findViewById(R.id.expense_category_spinner);
        Category cat = exp.category() != null ? exp.category() : Category.OTHER;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            if (categories[i] == cat) {
                categorySpinner.setSelection(i);
                break;
            }
        }

        Spinner payerSpinner = findViewById(R.id.expense_payer_spinner);
        int payerIndex = -1;
        for (int j = 0; j < memberProfiles.size(); j++) {
            if (memberProfiles.get(j).uid().equals(exp.payerId())) {
                payerIndex = j;
                break;
            }
        }
        if (payerIndex >= 0) {
            payerSpinner.setSelection(payerIndex);
        }

        List<String> partIds = exp.participantIds() != null ? exp.participantIds() : List.of();
        for (CheckBox box : participantBoxes) {
            String uid = (String) box.getTag();
            box.setChecked(partIds.contains(uid));
        }

        if (payerIndex >= 0) {
            updatePayerSelection(payerIndex);
        }
    }

    private void submit() {
        Spinner payerSpinner = findViewById(R.id.expense_payer_spinner);
        Spinner categorySpinner = findViewById(R.id.expense_category_spinner);

        int payerIdx = payerSpinner.getSelectedItemPosition();
        if (payerIdx < 0 || payerIdx >= memberProfiles.size()) return;
        String payerId = memberProfiles.get(payerIdx).uid();

        Category cat = (Category) categorySpinner.getSelectedItem();
        if (cat == null) {
            cat = Category.OTHER;
        }

        List<String> selectedParticipants = new ArrayList<>();
        for (CheckBox box : participantBoxes) {
            if (box.isChecked()) {
                selectedParticipants.add((String) box.getTag());
            }
        }

        if (isEditMode) {
            model.update(expenseId, input(R.id.expense_title), input(R.id.expense_amount), payerId, selectedParticipants, cat, input(R.id.expense_note));
        } else {
            model.create(input(R.id.expense_title), input(R.id.expense_amount), payerId, selectedParticipants, cat, input(R.id.expense_note));
        }
    }

    @Override
    protected void onAuthorized(User user) {
        if (model != null) {
            model.bindUser(user.uid());
            model.loadGroup();
            if (isEditMode) {
                model.loadExpense(expenseId);
            }
        }
    }

    @Override
    protected void onAccessLost() {
        if (model != null) {
            model.cancelOperations();
        }
    }
}
