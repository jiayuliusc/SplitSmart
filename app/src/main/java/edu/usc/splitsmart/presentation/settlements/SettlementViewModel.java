package edu.usc.splitsmart.presentation.settlements;

import androidx.lifecycle.*;
import java.time.LocalDate;
import java.util.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.*;
import edu.usc.splitsmart.service.settlements.SettlementRules;

public final class SettlementViewModel extends FeatureViewModel {
    private final SettlementService settlements;
    private final GroupService groups;
    private final String groupId;
    private final String settlementId;
    private String uid;
    public final MutableLiveData<List<MemberProfile>> members = new MutableLiveData<>(List.of());
    public final MutableLiveData<LedgerSnapshot> ledger = new MutableLiveData<>();
    public final MutableLiveData<Settlement> original = new MutableLiveData<>();
    public final MutableLiveData<MutationResult<?>> completed = new MutableLiveData<>();
    public SettlementViewModel(SavedStateHandle state, SettlementService settlements, GroupService groups, String groupId, String settlementId) {
        super(state); this.settlements = settlements; this.groups = groups; this.groupId = groupId; this.settlementId = settlementId;
    }
    @Override public void bindUser(String uid) {
        if (this.uid != null && !this.uid.equals(uid)) clearPrivate();
        super.bindUser(uid); this.uid = uid;
    }
    public void clearPrivate() {
        cancelOperations(); ledger.setValue(null); original.setValue(null); members.setValue(List.of()); completed.setValue(null);
    }
    public void load() {
        if (isBusy() || uid == null) return;
        start();
        track(settlements.getLedger(groupId, ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<LedgerSnapshot> result) {
                Settlement selected = null;
                for (Settlement item : result.value().settlements()) if (item.settlementId().equals(settlementId)) selected = item;
                if (settlementId != null && selected == null) { loadFailed(ErrorCode.NOT_FOUND); return; }
                if (selected != null && !uid.equals(selected.creatorId())) { loadFailed(ErrorCode.PERMISSION_DENIED); return; }
                if (selected != null && !saved.contains("draft:initialized")) {
                    editDraft("payee", selected.payeeId()); editDraft("amount", dollars(selected.amountCents()));
                    editDraft("note", selected.note() == null ? "" : selected.note()); editDraft("initialized", "yes");
                }
                original.setValue(selected); ledger.setValue(result.value());
                track(groups.getMemberProfiles(groupId, ReadPolicy.FRESH, new Callback<>() {
                    @Override public void onSuccess(ReadResult<List<MemberProfile>> value) { members.setValue(value.value()); busy.setValue(false); }
                    @Override public void onError(AppError failure) { readFailed(failure); }
                }));
            }
            @Override public void onError(AppError failure) { readFailed(failure); }
        }));
    }
    private void loadFailed(ErrorCode code) { readFailed(new AppError(code,"Settlement unavailable.",null,false)); }
    private void readFailed(AppError failure) {
        // A read result cannot resolve a write whose acknowledgement is still unknown.
        clearPrivate(); message.setValue(""); error.setValue(failure); busy.setValue(false);
    }
    public static String dollars(long cents) { return String.format(Locale.US, "%d.%02d", cents / 100, cents % 100); }
    public long remaining(String payeeId) {
        if (ledger.getValue() == null || uid == null || payeeId == null) return 0;
        return SettlementRules.remaining(ledger.getValue(), uid, payeeId, settlementId);
    }
    public void save(String payee, String amount, String note) {
        if (isBusy() || pending() || ledger.getValue() == null || uid == null) return;
        long cents;
        try { cents = SettlementRules.cents(amount); } catch (IllegalArgumentException invalid) { validation("amountCents"); return; }
        if (payee == null || uid.equals(payee) || !ledger.getValue().memberIds().contains(payee)) { validation("payeeId"); return; }
        if (cents > remaining(payee)) { validation("remaining"); return; }
        SettlementDraft draft = new SettlementDraft(payee, cents, note == null || note.trim().isEmpty() ? null : note.trim());
        if (SettlementRules.validate(draft) != null) { validation("note"); return; }
        persist(new SettlementIntent(settlementId == null ? "create" : "update", ledger.getValue().revision(), settlementId,
            settlementId == null ? LocalDate.now() : null, draft));
    }
    public void delete() {
        if (isBusy() || pending() || original.getValue() == null || ledger.getValue() == null) return;
        persist(new SettlementIntent("delete",ledger.getValue().revision(),settlementId,null,null));
    }
    private void validation(String field) { failed(new AppError(ErrorCode.VALIDATION,"Invalid settlement details.",field,false)); }
    private void persist(SettlementIntent payload) {
        intent(payload.operation(), payload.save().toArray(new String[0])); retry();
    }
    public void retry() {
        if (isBusy()) return;
        if (!pending()) { load(); return; }
        SettlementIntent payload = SettlementIntent.restore(values()); start();
        if ("delete".equals(payload.operation())) {
            track(settlements.deleteSettlement(groupId,payload.revision(),payload.settlementId(),requestId(), callback()));
        } else if ("update".equals(payload.operation())) {
            track(settlements.updateSettlement(groupId,payload.revision(),payload.settlementId(),payload.draft(),requestId(), callback()));
        } else {
            track(settlements.createSettlement(groupId,payload.revision(),payload.draft(),payload.date(),requestId(), callback()));
        }
    }
    private <T> Callback<MutationResult<T>> callback() {
        return new Callback<>() {
            @Override public void onSuccess(MutationResult<T> result) { done(""); completed.setValue(result); }
            @Override public void onError(AppError failure) {
                failed(failure);
                if (failure.code() == ErrorCode.CONFLICT) { load(); }
                else if (failure.code() == ErrorCode.PERMISSION_DENIED || failure.code() == ErrorCode.UNAUTHENTICATED || failure.code() == ErrorCode.GROUP_DELETING) clearPrivate();
            }
        };
    }
}
