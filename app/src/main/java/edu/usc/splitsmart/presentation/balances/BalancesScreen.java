package edu.usc.splitsmart.presentation.balances;

import android.os.Bundle;
import android.content.Intent;
import androidx.lifecycle.Lifecycle;
import androidx.core.widget.TextViewCompat;
import android.util.TypedValue;
import java.time.format.DateTimeFormatter;
import edu.usc.splitsmart.presentation.settlements.SettlementFormScreen;
import edu.usc.splitsmart.presentation.expenses.CreateExpenseScreen;
import edu.usc.splitsmart.presentation.expenses.ExpenseDetailScreen;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.view.ViewCompat;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

/** Live group balances and recorded payments, with fresh coherent reads after every change. */
public final class BalancesScreen extends ProtectedScreen {
    private BalancesViewModel model;
    private String groupId;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_balances);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.balances_title),true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.balances_people_heading),true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.balances_settlements_heading),true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.balances_expenses_heading),true);
        String id=getIntent().getStringExtra(NavigationContract.GROUP_ID);
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status,getString(R.string.groups_missing_scope));
            show(R.id.access_retry,false); show(R.id.access_logout,false); return;
        }
        groupId=id;
        model=new ViewModelProvider(this,new FeatureFactory(this,getIntent().getExtras(),
            saved -> new BalancesViewModel(saved,services().settlements(),services().groups(),services().groupUpdates(),id))).get(BalancesViewModel.class);
        bindErrors(model);
        model.content.observe(this,this::render);
        model.busy.observe(this,busy -> {
            show(R.id.feature_progress,Boolean.TRUE.equals(busy));
            findViewById(R.id.balances_refresh).setEnabled(!Boolean.TRUE.equals(busy));
            findViewById(R.id.balances_add_settlement).setEnabled(!Boolean.TRUE.equals(busy));
            findViewById(R.id.balances_add_expense).setEnabled(!Boolean.TRUE.equals(busy)); status();
        });
        model.message.observe(this,message -> status());
        findViewById(R.id.balances_refresh).setOnClickListener(v -> model.refresh());
        findViewById(R.id.balances_add_settlement).setOnClickListener(v -> paymentForm(null));
        findViewById(R.id.balances_add_expense).setOnClickListener(v -> startActivity(new Intent(this,CreateExpenseScreen.class).putExtra(NavigationContract.GROUP_ID,groupId)));
        protect();
    }
    private void status() {
        String message=model.isBusy() ? getString(R.string.balances_loading) : model.message.getValue();
        text(R.id.feature_message,message);
        show(R.id.feature_message,message != null && !message.isEmpty());
    }
    private void render(BalancesViewModel.Content content) {
        LinearLayout rows=findViewById(R.id.balances_rows); rows.removeAllViews();
        LinearLayout payments=findViewById(R.id.balances_settlements_rows);payments.removeAllViews();
        LinearLayout expenseRows=findViewById(R.id.balances_expenses_rows);expenseRows.removeAllViews();
        show(R.id.balances_data,content != null);
        if (content == null) {
            text(R.id.balances_group_name,""); text(R.id.balances_owe_amount,""); text(R.id.balances_receive_amount,""); return;
        }
        BalanceSummary summary=content.summary();
        text(R.id.balances_group_name,content.groupName());
        text(R.id.balances_owe_amount,money(summary.totalToPayCents()));
        text(R.id.balances_receive_amount,money(summary.totalToReceiveCents()));
        ((TextView)findViewById(R.id.balances_owe_amount)).setTextColor(getColor(summary.totalToPayCents()==0 ? R.color.balance_muted : R.color.balance_owe));
        ((TextView)findViewById(R.id.balances_receive_amount)).setTextColor(getColor(summary.totalToReceiveCents()==0 ? R.color.balance_muted : R.color.balance_receive));
        show(R.id.balances_empty,summary.totalToPayCents()==0 && summary.totalToReceiveCents()==0);
        text(R.id.balances_empty_detail,getString(summary.pairs().isEmpty() ? R.string.balances_solo : R.string.balances_zero_detail));
        show(R.id.balances_people_heading,!summary.pairs().isEmpty());
        for (PairBalance pair:summary.pairs()) {
            View row=getLayoutInflater().inflate(R.layout.row_balance,rows,false);
            String name=content.names().getOrDefault(pair.otherUserId(),getString(R.string.balances_member));
            String direction=getString(pair.netCents()>0 ? R.string.balances_pair_receive : pair.netCents()<0 ? R.string.balances_pair_owe : R.string.balances_pair_zero);
            String amount=money(Math.abs(pair.netCents()));
            ((TextView)row.findViewById(R.id.balance_member_name)).setText(name);
            ((TextView)row.findViewById(R.id.balance_direction)).setText(direction);
            TextView amountView=row.findViewById(R.id.balance_member_amount);
            amountView.setText(amount);
            if (amount.length() > 16) {
                LinearLayout line=row.findViewById(R.id.balance_row_content);
                line.setOrientation(LinearLayout.VERTICAL);
                View nameBlock=line.getChildAt(0);
                nameBlock.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams amountParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
                amountParams.topMargin=Math.round(8*getResources().getDisplayMetrics().density);
                amountView.setLayoutParams(amountParams); amountView.setGravity(Gravity.START);
            }
            amountView.setTextColor(getColor(pair.netCents()>0 ? R.color.balance_receive : pair.netCents()<0 ? R.color.balance_owe : R.color.balance_muted));
            row.setContentDescription(getString(R.string.balances_row_accessibility,name,direction,amount));
            rows.addView(row);
        }
        show(R.id.balances_settlements_empty,content.settlements().isEmpty());
        for(Settlement payment:content.settlements()) {
            View row=getLayoutInflater().inflate(R.layout.row_settlement,payments,false);
            String payer=content.names().getOrDefault(payment.payerId(),getString(R.string.balances_member));
            String payee=content.names().getOrDefault(payment.payeeId(),getString(R.string.balances_member));
            ((TextView)row.findViewById(R.id.settlement_row_names)).setText(getString(R.string.settlement_row_names,payer,payee));
            ((TextView)row.findViewById(R.id.settlement_row_date)).setText(payment.localDate().format(DateTimeFormatter.ofPattern("MMM d, uuuu",Locale.getDefault())));
            TextView note=row.findViewById(R.id.settlement_row_note);note.setText(payment.note());note.setVisibility(payment.note()==null || payment.note().isEmpty() ? View.GONE : View.VISIBLE);
            TextView amount=row.findViewById(R.id.settlement_row_amount);amount.setText(money(payment.amountCents()));amount.setMaxLines(1);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(amount,12,18,1,TypedValue.COMPLEX_UNIT_SP);
            View edit=row.findViewById(R.id.settlement_row_edit);
            edit.setVisibility(payment.creatorId().equals(summary.viewerId()) ? View.VISIBLE : View.GONE);
            edit.setOnClickListener(v -> paymentForm(payment.settlementId()));payments.addView(row);
        }
        show(R.id.balances_expenses_empty,content.expenses().isEmpty());
        for(Expense expense:content.expenses()) {
            View row=getLayoutInflater().inflate(R.layout.row_expense,expenseRows,false);
            ((TextView)row.findViewById(R.id.row_expense_title)).setText(expense.title());
            ((TextView)row.findViewById(R.id.row_expense_detail)).setText(expense.localDate().format(DateTimeFormatter.ofPattern("MMM d, uuuu",Locale.getDefault())));
            TextView amount=row.findViewById(R.id.row_expense_amount);amount.setText(money(expense.amountCents()));
            row.findViewById(R.id.row_expense_delete).setVisibility(View.GONE);
            row.setOnClickListener(v -> startActivity(new Intent(this,ExpenseDetailScreen.class)
                .putExtra(NavigationContract.GROUP_ID,groupId).putExtra(NavigationContract.EXPENSE_ID,expense.expenseId())));
            expenseRows.addView(row);
        }
    }
    private void paymentForm(String settlementId) {
        Intent intent=new Intent(this,SettlementFormScreen.class).putExtra(NavigationContract.GROUP_ID,groupId);
        if(settlementId!=null)intent.putExtra(NavigationContract.SETTLEMENT_ID,settlementId);
        startActivity(intent);
    }
    private static String money(long cents) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents,2));
    }
    @Override protected void onAuthorized(User user) {
        if(getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))model.activate(user.uid());
    }
    @Override protected void onPause() {super.onPause();if(model!=null)model.pause();}
    @Override protected void onAccessLost() { if (model != null) model.clear(); }
}
