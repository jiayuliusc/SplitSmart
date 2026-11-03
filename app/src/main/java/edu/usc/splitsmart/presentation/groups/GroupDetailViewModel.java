package edu.usc.splitsmart.presentation.groups;

import androidx.lifecycle.*;
import java.util.List;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.GroupService;

public final class GroupDetailViewModel extends FeatureViewModel {
    private final GroupService groups;
    private final String groupId;
    public final MutableLiveData<Group> group = new MutableLiveData<>();
    public final MutableLiveData<List<MemberProfile>> members = new MutableLiveData<>(List.of());
    public final MutableLiveData<MutationResult<GroupDeletion>> deleted = new MutableLiveData<>();
    public final MutableLiveData<MutationResult<Group>> changed = new MutableLiveData<>();
    public GroupDetailViewModel(SavedStateHandle state, GroupService groups, String groupId) {
        super(state); this.groups = groups; this.groupId = groupId;
    }
    public void refresh() {
        if (isBusy() || pending()) return; start();
        track(groups.getGroup(groupId, ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<Group> value) {
                group.setValue(value.value());
                track(groups.getMemberProfiles(groupId, ReadPolicy.FRESH, new Callback<>() {
                    @Override public void onSuccess(ReadResult<List<MemberProfile>> result) { members.setValue(result.value()); busy.setValue(false); }
                    @Override public void onError(AppError e) { failed(e); }
                }));
            }
            @Override public void onError(AppError e) { group.setValue(null); members.setValue(List.of()); failed(e); }
        }));
    }
    public void update(String name, String description) { begin("update", name, description); }
    public void add(String email) { begin("add", email); }
    public void delete() { begin("delete"); }
    private void begin(String op, String... fields) {
        if (isBusy() || pending() || group.getValue() == null) return;
        String[] payload = new String[fields.length + 1]; payload[0] = Long.toString(group.getValue().revision());
        System.arraycopy(fields, 0, payload, 1, fields.length); intent(op, payload); retry();
    }
    public void retry() {
        if (!pending() || isBusy()) return;
        List<String> input = values(); long revision = Long.parseLong(input.get(0)); start();
        if ("delete".equals(operation())) {
            track(groups.deleteGroup(groupId, revision, requestId(), new Callback<>() {
                @Override public void onSuccess(MutationResult<GroupDeletion> result) { done("Group deletion accepted."); deleted.setValue(result); }
                @Override public void onError(AppError e) { failed(e); }
            })); return;
        }
        Callback<MutationResult<Group>> callback = new Callback<>() {
            @Override public void onSuccess(MutationResult<Group> result) { group.setValue(result.value()); done("Group updated."); changed.setValue(result); refresh(); }
            @Override public void onError(AppError e) { failed(e); }
        };
        if ("add".equals(operation())) track(groups.addMember(groupId, revision, input.get(1), requestId(), callback));
        else track(groups.updateGroup(groupId, revision, new GroupDraft(input.get(1), input.get(2).isEmpty() ? null : input.get(2)), requestId(), callback));
    }
}
