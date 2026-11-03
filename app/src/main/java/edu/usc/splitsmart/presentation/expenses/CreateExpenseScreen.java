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

        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new ExpenseViewModel(saved, services().expenses(), services().groups(), groupId))).get(ExpenseViewModel.class);

        bind(model, R.id.expense_submit);

        // Setup Category Spinner
        Spinner categorySpinner = findViewById(R.id.expense_category_spinner);
        ArrayAdapter<Category> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Category.values());
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(catAdapter);

        model.members.observe(this, members -> {
            memberProfiles.clear();
            memberProfiles.addAll(members);

            // Populate Payer Spinner
            Spinner payerSpinner = findViewById(R.id.expense_payer_spinner);
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
        });

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

        model.create(input(R.id.expense_title), input(R.id.expense_amount), payerId, selectedParticipants, cat, input(R.id.expense_note));
    }

    @Override
    protected void onAuthorized(User user) {
        if (model != null) {
            model.bindUser(user.uid());
            model.loadGroup();
        }
    }

    @Override
    protected void onAccessLost() {
        if (model != null) {
            model.cancelOperations();
        }
    }
}
