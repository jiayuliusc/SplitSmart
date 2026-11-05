package edu.usc.splitsmart.presentation.expenses;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

public final class ExpenseDetailScreen extends ProtectedScreen {
    private ExpenseViewModel model;
    private final androidx.activity.result.ActivityResultLauncher<Intent> editLauncher = registerForActivityResult(
        new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                setResult(RESULT_OK, result.getData());
                if (model != null) model.loadExpense(getIntent().getStringExtra(NavigationContract.EXPENSE_ID));
            }
        });

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen(R.layout.screen_expense_detail);
        String groupId = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        String expenseId = getIntent().getStringExtra(NavigationContract.EXPENSE_ID);

        if (groupId == null || expenseId == null || !groupId.matches("[A-Za-z0-9_-]{1,128}") || !expenseId.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status, getString(R.string.groups_missing_scope));
            show(R.id.access_retry, false);
            show(R.id.access_logout, false);
            return;
        }

        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new ExpenseViewModel(saved, services().expenses(), services().groups(), groupId))).get(ExpenseViewModel.class);

        bind(model, R.id.expense_delete, R.id.expense_edit);

        model.busy.observe(this, busy -> {
            if (!Boolean.TRUE.equals(busy) && model.ledger.getValue() == null) show(R.id.identity_retry, true);
        });
        model.expense.observe(this, exp -> {
            if (exp == null) {
                show(R.id.expense_delete, false); show(R.id.expense_edit, false);
                text(R.id.feature_title, getString(R.string.expenses_detail_title));
                for (int id : new int[]{R.id.expense_detail_amount, R.id.expense_detail_category, R.id.expense_detail_date,
                        R.id.expense_detail_note, R.id.expense_detail_payer}) text(id, "");
                ((LinearLayout) findViewById(R.id.expense_shares_container)).removeAllViews();
                return;
            }
            text(R.id.feature_title, exp.title());
            text(R.id.expense_detail_amount, String.format(Locale.US, "$%.2f", exp.amountCents() / 100.0));
            text(R.id.expense_detail_category, getString(R.string.expenses_category) + ": " + exp.category().name());
            text(R.id.expense_detail_date, getString(R.string.expenses_date) + ": " + exp.localDate().toString());
            text(R.id.expense_detail_note, exp.note() == null || exp.note().isEmpty() ? "" : (getString(R.string.expenses_note) + ": " + exp.note()));

            boolean isCreator = currentUid != null && currentUid.equals(exp.creatorId());
            show(R.id.expense_delete, isCreator);
            show(R.id.expense_edit, isCreator);

            // Match member names for payer and shares
            List<MemberProfile> mems = model.members.getValue();
            if (mems != null) {
                String payerName = exp.payerId();
                for (MemberProfile m : mems) {
                    if (m.uid().equals(exp.payerId())) {
                        payerName = m.name();
                        break;
                    }
                }
                text(R.id.expense_detail_payer, getString(R.string.expenses_payer) + ": " + payerName);

                LinearLayout sharesContainer = findViewById(R.id.expense_shares_container);
                sharesContainer.removeAllViews();
                for (ExpenseShare share : exp.shares()) {
                    String pName = share.participantId();
                    for (MemberProfile m : mems) {
                        if (m.uid().equals(share.participantId())) {
                            pName = m.name();
                            break;
                        }
                    }
                    TextView tv = new TextView(this);
                    tv.setText(String.format(Locale.US, "%s: $%.2f", pName, share.amountCents() / 100.0));
                    tv.setTextSize(16f);
                    tv.setTextColor(0xFF30292A);
                    tv.setPadding(0, 4, 0, 4);
                    sharesContainer.addView(tv);
                }
            }
        });

        findViewById(R.id.expense_edit).setOnClickListener(v -> editLauncher.launch(new Intent(this, CreateExpenseScreen.class)
            .putExtra(NavigationContract.GROUP_ID, groupId).putExtra(NavigationContract.EXPENSE_ID, expenseId)));
        findViewById(R.id.expense_delete).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.expenses_delete_title)
            .setMessage(R.string.expenses_delete_message)
            .setNegativeButton(R.string.groups_cancel, null)
            .setPositiveButton(R.string.expenses_delete, (dialog, which) -> {
                if (model.expense.getValue() != null) {
                    model.delete(model.expense.getValue().expenseId());
                }
            }).show());

        findViewById(R.id.identity_retry).setOnClickListener(v -> model.retry());

        model.deleted.observe(this, result -> {
            if (result == null) return;
            setResult(RESULT_OK, new Intent()
                .putExtra(NavigationContract.RESULT_REQUEST_ID, result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID, groupId)
                .putExtra(NavigationContract.RESULT_GROUP_REVISION, result.groupRevision().longValue()));
            finish();
        });

        protect();
    }

    @Override
    protected void onAuthorized(User user) {
        if (model != null) {
            model.bindUser(user.uid());
            String expId = getIntent().getStringExtra(NavigationContract.EXPENSE_ID);
            if (expId != null) model.loadExpense(expId);
        }
    }

    @Override
    protected void onAccessLost() {
        if (model != null) {
            model.cancelOperations();
        }
    }
}
