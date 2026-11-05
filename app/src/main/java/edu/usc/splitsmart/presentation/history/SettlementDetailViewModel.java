package edu.usc.splitsmart.presentation.history;

import androidx.lifecycle.*;
import java.util.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.*;

/** Read-only view of one settlement, available to every group member. Only the creator may edit. */
public final class SettlementDetailViewModel extends FeatureViewModel {
    public record Content(Settlement settlement, Map<String, String> names, boolean canEdit) { }
    public final MutableLiveData<Content> content = new MutableLiveData<>();

    private final SettlementService settlements;
    private final GroupService groups;
    private final String groupId;
    private final String settlementId;
    private String viewer;
    private boolean active;
    private int generation;

    public SettlementDetailViewModel(SavedStateHandle state, SettlementService settlements, GroupService groups,
                                     String groupId, String settlementId) {
        super(state);
        this.settlements = settlements; this.groups = groups;
        this.groupId = groupId; this.settlementId = settlementId;
    }

    /** Called from onAuthorized: on first open and again on every resume (e.g. after editing). */
    public void activate(String uid) {
        if (active && Objects.equals(viewer, uid)) return;
        clear(); bindUser(uid); viewer = uid; active = true; refresh();
    }

    public void refresh() {
        if (!active || viewer == null || isBusy()) return;
        cancelOperations();
        int read = ++generation;
        start();
        track(settlements.getSettlement(groupId, settlementId, ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<Settlement> result) {
                if (read != generation) return;
                loadNames(read, result.value());
            }
            @Override public void onError(AppError error) { fail(read, error); }
        }));
    }

    private void loadNames(int read, Settlement settlement) {
        track(groups.getMemberProfiles(groupId, ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<List<MemberProfile>> result) {
                if (read != generation) return;
                Map<String, String> names = new HashMap<>();
                for (MemberProfile member : result.value()) names.put(member.uid(), member.name());
                content.setValue(new Content(settlement, Map.copyOf(names), settlement.creatorId().equals(viewer)));
                done("");
            }
            @Override public void onError(AppError error) { fail(read, error); }
        }));
    }

    private void fail(int read, AppError error) {
        if (!active || read != generation) return;
        content.setValue(null); failed(error);
    }

    public void pause() { active = false; ++generation; cancelOperations(); }
    public void clear() { pause(); viewer = null; content.setValue(null); }
    @Override protected void onCleared() { pause(); super.onCleared(); }
}
