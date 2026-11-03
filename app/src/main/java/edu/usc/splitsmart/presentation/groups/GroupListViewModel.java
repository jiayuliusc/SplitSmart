package edu.usc.splitsmart.presentation.groups;

import androidx.lifecycle.*;
import java.util.List;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.GroupService;

public final class GroupListViewModel extends FeatureViewModel {
    private final GroupService groups;
    public final MutableLiveData<List<Group>> list = new MutableLiveData<>(List.of());
    public final MutableLiveData<MutationResult<Group>> created = new MutableLiveData<>();
    public GroupListViewModel(SavedStateHandle state, GroupService groups) { super(state); this.groups = groups; }
    public void refresh() {
        if (isBusy()) return; start();
        track(groups.getGroupsForCurrentUser(ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<List<Group>> value) { list.setValue(value.value()); busy.setValue(false); }
            @Override public void onError(AppError e) { list.setValue(List.of()); failed(e); }
        }));
    }
    public void create(String name, String description) {
        if (isBusy()) return;
        String key = intent("create", name, description); List<String> values = values(); start();
        track(groups.createGroup(new GroupDraft(values.get(0), values.get(1).isEmpty() ? null : values.get(1)), key, new Callback<>() {
            @Override public void onSuccess(MutationResult<Group> result) { done("Group created."); created.setValue(result); }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }
}
