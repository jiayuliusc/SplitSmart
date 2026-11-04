package edu.usc.splitsmart.presentation.settlements;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import java.util.*;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

public final class SettlementFormScreen extends ProtectedScreen {
    private SettlementViewModel model;
    private final List<MemberProfile> payees = new ArrayList<>();
    private boolean renderingPayees;
    private String payerName;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_settlement_form);
        String groupId = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        String settlementId = getIntent().getStringExtra(NavigationContract.SETTLEMENT_ID);
        if (!validId(groupId) || (settlementId != null && !validId(settlementId))) {
            text(R.id.access_status,getString(R.string.groups_missing_scope));
            show(R.id.access_retry,false); show(R.id.access_logout,false); return;
        }
        model = new ViewModelProvider(this,new FeatureFactory(this,getIntent().getExtras(),
            saved -> new SettlementViewModel(saved,services().settlements(),services().groups(),groupId,settlementId))).get(SettlementViewModel.class);
        text(R.id.feature_title,getString(settlementId == null ? R.string.settlement_create_title : R.string.settlement_edit_title));
        bind(model,R.id.settlement_submit,R.id.settlement_delete);
        bindDraft(model,R.id.settlement_amount,"amount"); bindDraft(model,R.id.settlement_note,"note");
        text(R.id.settlement_amount,model.draft("amount","")); text(R.id.settlement_note,model.draft("note",""));
        Spinner spinner = findViewById(R.id.settlement_payee_spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                if (renderingPayees || position < 0 || position >= payees.size()) return;
                model.editDraft("payee",payees.get(position).uid()); renderLimit();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        model.members.observe(this, members -> renderMembers(members));
        model.ledger.observe(this, ledger -> {
            if (ledger != null) {
                text(R.id.settlement_amount,model.draft("amount","")); text(R.id.settlement_note,model.draft("note",""));
            }
            renderLimit();
        });
        model.original.observe(this, original -> show(R.id.settlement_delete, original != null));
        model.busy.observe(this, busy -> {
            spinner.setEnabled(!Boolean.TRUE.equals(busy) && !model.pending() && model.ledger.getValue() != null);
            boolean available = model.ledger.getValue() != null;
            findViewById(R.id.settlement_submit).setEnabled(!Boolean.TRUE.equals(busy) && !model.pending() && available);
            if (!model.pending() && !Boolean.TRUE.equals(busy) && !available) show(R.id.identity_retry,true);
        });
        findViewById(R.id.settlement_submit).setOnClickListener(view -> model.save(selectedPayee(),input(R.id.settlement_amount),input(R.id.settlement_note)));
        findViewById(R.id.settlement_delete).setOnClickListener(view -> new AlertDialog.Builder(this)
            .setTitle(R.string.settlement_delete_title).setMessage(R.string.settlement_delete_message)
            .setNegativeButton(R.string.settlement_cancel,null)
            .setPositiveButton(R.string.settlement_delete,(dialog,which) -> model.delete()).show());
        findViewById(R.id.identity_retry).setOnClickListener(view -> model.retry());
        model.completed.observe(this,result -> {
            if (result == null) return;
            setResult(RESULT_OK,new Intent().putExtra(NavigationContract.RESULT_REQUEST_ID,result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID,groupId)
                .putExtra(NavigationContract.RESULT_GROUP_REVISION,result.groupRevision().longValue())); finish();
        });
        protect();
    }
    private static boolean validId(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,128}"); }
    private void renderMembers(List<MemberProfile> members) {
        if (currentUid == null) return;
        String selected = model.draft("payee","");
        renderingPayees = true;
        try {
            payees.clear(); List<String> names = new ArrayList<>();
            for (MemberProfile member : members) {
                if (currentUid.equals(member.uid())) payerName = member.name();
                else { payees.add(member); names.add(member.name()); }
            }
            Spinner spinner = findViewById(R.id.settlement_payee_spinner);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
            int position = 0;
            for (int i = 0; i < payees.size(); i++) if (payees.get(i).uid().equals(selected)) position = i;
            if (!payees.isEmpty()) { spinner.setSelection(position); model.editDraft("payee",payees.get(position).uid()); }
            text(R.id.settlement_payer,getString(R.string.settlement_payer,payerName == null ? "" : payerName));
        } finally { renderingPayees = false; }
        renderLimit();
    }
    private String selectedPayee() {
        int position = ((Spinner)findViewById(R.id.settlement_payee_spinner)).getSelectedItemPosition();
        return position >= 0 && position < payees.size() ? payees.get(position).uid() : null;
    }
    private void renderLimit() {
        text(R.id.settlement_limit,getString(R.string.settlement_remaining,SettlementViewModel.dollars(model.remaining(selectedPayee()))));
    }
    @Override protected void bindErrors(FeatureViewModel viewModel) {
        viewModel.error.observe(this,error -> {
            if (error == null) return;
            int resource = R.string.settlement_error;
            if (error.code() == ErrorCode.VALIDATION || error.code() == ErrorCode.OUT_OF_RANGE) {
                resource = switch (error.field() == null ? "" : error.field().replace("draft.","")) {
                    case "amountCents" -> R.string.settlement_invalid_amount;
                    case "remaining" -> R.string.settlement_overpayment;
                    case "payeeId" -> R.string.settlement_invalid_payee;
                    case "note" -> R.string.settlement_invalid_note;
                    default -> R.string.settlement_error;
                };
            } else if (error.code() == ErrorCode.CONFLICT) resource = R.string.settlement_conflict;
            else if (error.code() == ErrorCode.PERMISSION_DENIED || error.code() == ErrorCode.UNAUTHENTICATED || error.code() == ErrorCode.GROUP_DELETING) resource = R.string.settlement_access_error;
            else if (error.code() == ErrorCode.NOT_FOUND) resource = R.string.settlement_unavailable;
            else if (error.retryable() || error.code() == ErrorCode.INTERNAL) resource = R.string.settlement_retry_error;
            showError(getString(resource)); viewModel.error.setValue(null);
        });
    }
    @Override protected void onAuthorized(User user) {
        if (model == null) return;
        payerName = user.name(); model.bindUser(user.uid()); model.load();
    }
    @Override protected void onAccessLost() {
        if (model != null) { model.clearPrivate(); payees.clear(); payerName = null; }
    }
}
