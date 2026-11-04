package edu.usc.splitsmart.presentation.groups;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Locale;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.expenses.*;
import edu.usc.splitsmart.presentation.balances.BalancesScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

public final class GroupDetailScreen extends ProtectedScreen {
    private GroupDetailViewModel model;
    private ExpenseViewModel expenseModel;

    private final ActivityResultLauncher<Intent> expenseLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                if (model != null) model.refresh();
                if (expenseModel != null) expenseModel.refreshLedger();
            }
        });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_group_detail);
        String id = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status, getString(R.string.groups_missing_scope)); show(R.id.access_retry, false); show(R.id.access_logout, false); return;
        }
        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new GroupDetailViewModel(saved, services().groups(), id))).get(GroupDetailViewModel.class);
        expenseModel = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new ExpenseViewModel(saved, services().expenses(), services().groups(), id))).get(ExpenseViewModel.class);

        bind(model, R.id.groups_refresh, R.id.groups_save, R.id.groups_add, R.id.groups_delete, R.id.groups_balances);
        findViewById(R.id.groups_balances).setOnClickListener(v -> startActivity(new Intent(this, BalancesScreen.class)
            .putExtra(NavigationContract.GROUP_ID, id)));
        bindDraft(model, R.id.groups_name, "groupName");
        bindDraft(model, R.id.groups_description, "groupDescription");
        model.group.observe(this, group -> {
            if (group == null) {
                show(R.id.groups_owner_controls, false);
                text(R.id.feature_title, getString(R.string.groups_details_title));
                text(R.id.feature_intro, ""); text(R.id.groups_description_view, ""); text(R.id.groups_role, "");
                return;
            }
            text(R.id.feature_title, group.name()); text(R.id.feature_intro, getString(R.string.groups_details_intro));
            text(R.id.groups_description_view, group.description() == null ? "" : group.description());
            boolean owner = group.ownerId().equals(currentUid); show(R.id.groups_owner_controls, owner);
            text(R.id.groups_role, getString(owner ? R.string.groups_owner_label : R.string.groups_member_label));
            text(R.id.groups_name, model.draft("groupName", group.name()));
            text(R.id.groups_description, model.draft("groupDescription", group.description() == null ? "" : group.description()));
        });
        model.members.observe(this, members -> {
            LinearLayout rows = findViewById(R.id.groups_members_rows); rows.removeAllViews();
            for (MemberProfile member : members) {
                View row = getLayoutInflater().inflate(R.layout.row_group, rows, false);
                row.setClickable(false); row.setFocusable(false);
                ((TextView) row.findViewById(R.id.row_name)).setText(member.name());
                ((TextView) row.findViewById(R.id.row_detail)).setText(member.email()); rows.addView(row);
            }
        });

        expenseModel.ledger.observe(this, ledger -> {
            LinearLayout rows = findViewById(R.id.expenses_rows);
            rows.removeAllViews();
            if (ledger == null || ledger.expenses().isEmpty()) {
                TextView emptyTv = new TextView(this);
                emptyTv.setText(R.string.expenses_empty);
                emptyTv.setTextSize(14f);
                emptyTv.setTextColor(0xFF605557);
                emptyTv.setPadding(0, 8, 0, 16);
                rows.addView(emptyTv);
                return;
            }
            for (Expense exp : ledger.expenses()) {
                View row = getLayoutInflater().inflate(R.layout.row_expense, rows, false);
                ((TextView) row.findViewById(R.id.row_expense_title)).setText(exp.title());
                ((TextView) row.findViewById(R.id.row_expense_detail)).setText(String.format(Locale.US, "%s • %s", exp.category().name(), exp.localDate().toString()));
                ((TextView) row.findViewById(R.id.row_expense_amount)).setText(String.format(Locale.US, "$%.2f", exp.amountCents() / 100.0));

                View deleteBtn = row.findViewById(R.id.row_expense_delete);
                boolean isCreator = currentUid != null && currentUid.equals(exp.creatorId());
                if (isCreator) {
                    deleteBtn.setVisibility(View.VISIBLE);
                    deleteBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.expenses_delete_title)
                        .setMessage(R.string.expenses_delete_message)
                        .setNegativeButton(R.string.groups_cancel, null)
                        .setPositiveButton(R.string.expenses_delete, (dialog, which) -> expenseModel.delete(exp.expenseId()))
                        .show());
                } else {
                    deleteBtn.setVisibility(View.GONE);
                }

                row.setOnClickListener(v -> expenseLauncher.launch(new Intent(this, ExpenseDetailScreen.class)
                    .putExtra(NavigationContract.GROUP_ID, id)
                    .putExtra(NavigationContract.EXPENSE_ID, exp.expenseId())));
                rows.addView(row);
            }
        });

        findViewById(R.id.expenses_record_button).setOnClickListener(v -> expenseLauncher.launch(new Intent(this, CreateExpenseScreen.class)
            .putExtra(NavigationContract.GROUP_ID, id)));

        expenseModel.deleted.observe(this, result -> {
            if (result != null) {
                expenseModel.refreshLedger();
            }
        });
        expenseModel.created.observe(this, result -> {
            if (result != null) {
                expenseModel.refreshLedger();
            }
        });

        model.deleted.observe(this, result -> {
            if (result == null) return;
            setResult(RESULT_OK, new Intent().putExtra(NavigationContract.RESULT_REQUEST_ID, result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID, result.value().groupId())
                .putExtra(NavigationContract.RESULT_GROUP_REVISION, result.groupRevision().longValue())); finish();
        });
        model.changed.observe(this, result -> {
            if (result != null) setResult(RESULT_OK, new Intent()
                .putExtra(NavigationContract.RESULT_REQUEST_ID, result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID, result.value().groupId())
                .putExtra(NavigationContract.RESULT_GROUP_REVISION, result.groupRevision().longValue()));
        });
        findViewById(R.id.groups_refresh).setOnClickListener(v -> {
            model.refresh();
            expenseModel.refreshLedger();
        });
        findViewById(R.id.groups_save).setOnClickListener(v -> model.update(input(R.id.groups_name), input(R.id.groups_description)));
        findViewById(R.id.groups_add).setOnClickListener(v -> model.add(input(R.id.groups_email)));
        findViewById(R.id.groups_delete).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.groups_delete_title).setMessage(R.string.groups_delete_message)
            .setNegativeButton(R.string.groups_cancel, null).setPositiveButton(R.string.groups_delete, (dialog, which) -> model.delete()).show());
        findViewById(R.id.identity_retry).setOnClickListener(v -> model.retry()); protect();
    }
    @Override protected void onAuthorized(User user) {
        if (model != null) {
            model.bindUser(user.uid());
            model.refresh();
        }
        if (expenseModel != null) {
            expenseModel.bindUser(user.uid());
            expenseModel.loadGroup();
            expenseModel.refreshLedger();
        }
    }
    @Override protected void onAccessLost() {
        if (model != null) { model.cancelOperations(); model.group.setValue(null); model.members.setValue(java.util.List.of()); }
        if (expenseModel != null) { expenseModel.cancelOperations(); expenseModel.ledger.setValue(null); }
    }
}
