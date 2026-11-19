package edu.usc.splitsmart.presentation.history;

import android.content.Intent;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;
import edu.usc.splitsmart.presentation.settlements.SettlementFormScreen;

/** Complete information for one settlement, opened from the group history. */
public final class SettlementDetailScreen extends ProtectedScreen {
    private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,128}";
    private SettlementDetailViewModel model;
    private String groupId;
    private String settlementId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen(R.layout.screen_settlement_detail);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.settlement_detail_title), true);

        groupId = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        settlementId = getIntent().getStringExtra(NavigationContract.SETTLEMENT_ID);
        if (groupId == null || !groupId.matches(ID_PATTERN) || settlementId == null || !settlementId.matches(ID_PATTERN)) {
            text(R.id.access_status, getString(R.string.groups_missing_scope));
            show(R.id.access_retry, false); show(R.id.access_logout, false);
            return;
        }

        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new SettlementDetailViewModel(saved, services().settlements(), services().groups(), groupId, settlementId)))
            .get(SettlementDetailViewModel.class);
        bindErrors(model);
        model.content.observe(this, this::render);
        model.busy.observe(this, busy -> {
            show(R.id.feature_progress, Boolean.TRUE.equals(busy));
            findViewById(R.id.settlement_detail_edit).setEnabled(!Boolean.TRUE.equals(busy));
        });
        findViewById(R.id.settlement_detail_edit).setOnClickListener(v -> startActivity(
            new Intent(this, SettlementFormScreen.class)
                .putExtra(NavigationContract.GROUP_ID, groupId)
                .putExtra(NavigationContract.SETTLEMENT_ID, settlementId)));
        protect();
    }

    private void render(SettlementDetailViewModel.Content content) {
        show(R.id.settlement_detail_fields, content != null);
        show(R.id.settlement_detail_edit, content != null && content.canEdit());
        if (content == null) return;
        Settlement s = content.settlement();
        String member = getString(R.string.balances_member);
        text(R.id.settlement_detail_amount, money(s.amountCents()));
        text(R.id.settlement_detail_payer, content.names().getOrDefault(s.payerId(), member));
        text(R.id.settlement_detail_payee, content.names().getOrDefault(s.payeeId(), member));
        text(R.id.settlement_detail_date, s.localDate().toString());   // YYYY-MM-DD, as the spec requires
        boolean hasNote = s.note() != null && !s.note().isEmpty();
        text(R.id.settlement_detail_note, hasNote ? s.note() : getString(R.string.history_no_note));
    }

    private static String money(long cents) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2));
    }

    @Override protected void onAuthorized(User user) {
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) model.activate(user.uid());
    }
    @Override protected void onPause() { super.onPause(); if (model != null) model.pause(); }
    @Override protected void onAccessLost() { if (model != null) model.clear(); }
}
