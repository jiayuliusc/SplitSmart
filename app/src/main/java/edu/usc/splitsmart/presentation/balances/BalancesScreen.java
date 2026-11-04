package edu.usc.splitsmart.presentation.balances;

import android.os.Bundle;
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

/** Read-only group balances, with explicit debt directions and fresh data on every resume. */
public final class BalancesScreen extends ProtectedScreen {
    private BalancesViewModel model;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_balances);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.balances_title),true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.balances_people_heading),true);
        String id=getIntent().getStringExtra(NavigationContract.GROUP_ID);
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status,getString(R.string.groups_missing_scope));
            show(R.id.access_retry,false); show(R.id.access_logout,false); return;
        }
        model=new ViewModelProvider(this,new FeatureFactory(this,getIntent().getExtras(),
            saved -> new BalancesViewModel(saved,services().balances(),services().groups(),id))).get(BalancesViewModel.class);
        model.content.observe(this,this::render);
        model.busy.observe(this,busy -> {
            show(R.id.feature_progress,Boolean.TRUE.equals(busy));
            findViewById(R.id.balances_refresh).setEnabled(!Boolean.TRUE.equals(busy)); status();
        });
        model.message.observe(this,message -> status());
        findViewById(R.id.balances_refresh).setOnClickListener(v -> model.refresh());
        protect();
    }
    private void status() {
        String message=model.isBusy() ? getString(R.string.balances_loading) : model.message.getValue();
        text(R.id.feature_message,message);
        show(R.id.feature_message,message != null && !message.isEmpty());
    }
    private void render(BalancesViewModel.Content content) {
        LinearLayout rows=findViewById(R.id.balances_rows); rows.removeAllViews();
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
    }
    private static String money(long cents) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents,2));
    }
    @Override protected void onAuthorized(User user) { model.bindUser(user.uid()); model.refresh(); }
    @Override protected void onAccessLost() { if (model != null) model.clear(); }
}
