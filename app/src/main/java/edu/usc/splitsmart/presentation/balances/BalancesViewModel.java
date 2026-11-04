package edu.usc.splitsmart.presentation.balances;

import androidx.lifecycle.*;
import java.util.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.*;

/** Reloads on resume/refresh; private rows are removed whenever a fresh read begins or fails. */
public final class BalancesViewModel extends FeatureViewModel {
    public record Content(String groupName, BalanceSummary summary, Map<String,String> names) { }
    public final MutableLiveData<Content> content = new MutableLiveData<>();
    private final BalanceService balances;
    private final GroupService groups;
    private final String groupId;
    private int generation;
    public BalancesViewModel(SavedStateHandle state, BalanceService balances, GroupService groups, String groupId) {
        super(state); this.balances=balances; this.groups=groups; this.groupId=groupId;
    }
    public void refresh() {
        cancelOperations(); int read = ++generation; content.setValue(null); start();
        track(groups.getGroup(groupId,ReadPolicy.FRESH,new Callback<>() {
            @Override public void onSuccess(ReadResult<Group> group) {
                if (read != generation) return;
                track(balances.getBalances(groupId,ReadPolicy.FRESH,new Callback<>() {
                    @Override public void onSuccess(ReadResult<BalanceSummary> summary) {
                        if (read != generation) return;
                        track(groups.getMemberProfiles(groupId,ReadPolicy.FRESH,new Callback<>() {
                            @Override public void onSuccess(ReadResult<List<MemberProfile>> members) {
                                if (read != generation) return;
                                Map<String,String> names = new HashMap<>();
                                for (MemberProfile member : members.value()) names.put(member.uid(),member.name());
                                content.setValue(new Content(group.value().name(),summary.value(),Map.copyOf(names)));
                                done("");
                            }
                            @Override public void onError(AppError error) { fail(read,error); }
                        }));
                    }
                    @Override public void onError(AppError error) { fail(read,error); }
                }));
            }
            @Override public void onError(AppError error) { fail(read,error); }
        }));
    }
    private void fail(int read, AppError error) {
        if (read != generation) return;
        content.setValue(null); failed(error);
    }
    public void clear() { ++generation; cancelOperations(); content.setValue(null); }
}
