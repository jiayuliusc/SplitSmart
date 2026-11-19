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
    private static final String PAID = "paid";          // I paid the other member
    private static final String RECEIVED = "received";  // the other member paid me
    private SettlementViewModel model;
    private final List<MemberProfile> others = new ArrayList<>();
    private boolean renderingMembers;

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

        // Direction: "I paid them" or "They paid me". Restored from the draft after rotation.
        RadioGroup direction = findViewById(R.id.settlement_direction);
        direction.check(RECEIVED.equals(model.draft("direction", PAID)) ? R.id.settlement_they_paid : R.id.settlement_i_paid);
        direction.setOnCheckedChangeListener((group, checkedId) -> {
            if (renderingMembers) return;
            model.editDraft("direction", checkedId == R.id.settlement_they_paid ? RECEIVED : PAID);
            renderLimit();
        });

        // The other party: every group member except the signed-in user.
        Spinner spinner = findViewById(R.id.settlement_payee_spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                if (renderingMembers || position < 0 || position >= others.size()) return;
                model.editDraft("other",others.get(position).uid()); renderLimit();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        model.members.observe(this, this::renderMembers);
        model.ledger.observe(this, ledger -> {
            if (ledger != null) {
                text(R.id.settlement_amount,model.draft("amount","")); text(R.id.settlement_note,model.draft("note",""));
            }
            renderLimit();
        });
        model.original.observe(this, original -> show(R.id.settlement_delete, original != null));
        model.busy.observe(this, busy -> {
            boolean available = model.ledger.getValue() != null;
            boolean idle = !Boolean.TRUE.equals(busy) && !model.pending();
            setPartiesEnabled(idle && available && !model.editing());   // parties are locked when editing
            findViewById(R.id.settlement_submit).setEnabled(idle && available);
            if (idle && !available) show(R.id.identity_retry,true);
        });
        show(R.id.settlement_parties_locked, model.editing());

        findViewById(R.id.settlement_submit).setOnClickListener(view ->
            model.save(payer(),payee(),input(R.id.settlement_amount),input(R.id.settlement_note)));
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
        // When editing, the parties come from the stored settlement and cannot change.
        Settlement original = model.original.getValue();
        if (original != null) {
            boolean iPaid = currentUid.equals(original.payerId());
            model.editDraft("direction", iPaid ? PAID : RECEIVED);
            model.editDraft("other", iPaid ? original.payeeId() : original.payerId());
        }
        String selected = model.draft("other","");
        renderingMembers = true;
        try {
            ((RadioGroup) findViewById(R.id.settlement_direction)).check(
                RECEIVED.equals(model.draft("direction", PAID)) ? R.id.settlement_they_paid : R.id.settlement_i_paid);
            others.clear(); List<String> names = new ArrayList<>();
            for (MemberProfile member : members) {
                if (!currentUid.equals(member.uid())) { others.add(member); names.add(member.name()); }
            }
            Spinner spinner = findViewById(R.id.settlement_payee_spinner);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
            int position = 0;
            for (int i = 0; i < others.size(); i++) if (others.get(i).uid().equals(selected)) position = i;
            if (!others.isEmpty()) { spinner.setSelection(position); model.editDraft("other",others.get(position).uid()); }
        } finally { renderingMembers = false; }
        renderLimit();
    }

    private String otherMember() {
        int position = ((Spinner)findViewById(R.id.settlement_payee_spinner)).getSelectedItemPosition();
        return position >= 0 && position < others.size() ? others.get(position).uid() : null;
    }
    private boolean theyPaid() {
        return ((RadioGroup) findViewById(R.id.settlement_direction)).getCheckedRadioButtonId() == R.id.settlement_they_paid;
    }
    private String payer() { return theyPaid() ? otherMember() : currentUid; }
    private String payee() { return theyPaid() ? currentUid : otherMember(); }

    private void setPartiesEnabled(boolean enabled) {
        findViewById(R.id.settlement_payee_spinner).setEnabled(enabled);
        findViewById(R.id.settlement_i_paid).setEnabled(enabled);      // RadioGroup.setEnabled does not reach its buttons
        findViewById(R.id.settlement_they_paid).setEnabled(enabled);
    }

    private void renderLimit() {
        text(R.id.settlement_limit,getString(theyPaid() ? R.string.settlement_remaining_received : R.string.settlement_remaining,
            SettlementViewModel.dollars(model.remaining(payer(),payee()))));
    }

    @Override protected void bindErrors(FeatureViewModel viewModel) {
        viewModel.error.observe(this,error -> {
            if (error == null) return;
            int resource = R.string.settlement_error;
            if (error.code() == ErrorCode.VALIDATION || error.code() == ErrorCode.OUT_OF_RANGE) {
                resource = switch (error.field() == null ? "" : error.field().replace("draft.","")) {
                    case "amountCents" -> R.string.settlement_invalid_amount;
                    case "remaining" -> R.string.settlement_overpayment;
                    case "payeeId", "payerId" -> R.string.settlement_invalid_payee;
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
        model.bindUser(user.uid()); model.load();
    }
    @Override protected void onAccessLost() {
        if (model != null) { model.clearPrivate(); others.clear(); }
    }
}
