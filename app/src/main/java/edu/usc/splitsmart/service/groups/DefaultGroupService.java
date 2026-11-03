package edu.usc.splitsmart.service.groups;

import java.util.List;
import java.util.concurrent.Executor;
import edu.usc.splitsmart.data.repository.GroupRepository;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.domain.validation.IdentityValidation;
import edu.usc.splitsmart.service.GroupService;
import edu.usc.splitsmart.util.PendingOperation;

public final class DefaultGroupService implements GroupService {
    private final GroupRepository groups;
    private final Executor delivery;
    public DefaultGroupService(GroupRepository groups, Executor delivery) { this.groups = groups; this.delivery = delivery; }
    private <T> AsyncOperation invalid(AppError error, Callback<T> callback) {
        PendingOperation<T> op = new PendingOperation<>(delivery, callback); op.onError(error); return op;
    }
    @Override public AsyncOperation getGroupsForCurrentUser(ReadPolicy policy, Callback<ReadResult<List<Group>>> callback) { return groups.getGroupsForCurrentUser(policy, callback); }
    @Override public AsyncOperation getGroup(String id, ReadPolicy policy, Callback<ReadResult<Group>> callback) { return groups.getGroup(id, policy, callback); }
    @Override public AsyncOperation getMemberProfiles(String id, ReadPolicy policy, Callback<ReadResult<List<MemberProfile>>> callback) { return groups.getMemberProfiles(id, policy, callback); }
    @Override public AsyncOperation createGroup(GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback) {
        AppError error = IdentityValidation.group(draft);
        return error == null ? groups.createGroup(new GroupDraft(draft.name().trim(), draft.description()), requestId, callback) : invalid(error, callback);
    }
    @Override public AsyncOperation addMember(String id, long revision, String email, String requestId, Callback<MutationResult<Group>> callback) {
        AppError error = IdentityValidation.email(email);
        return error == null ? groups.addMember(id, revision, IdentityValidation.normalizeEmail(email), requestId, callback) : invalid(error, callback);
    }
    @Override public AsyncOperation updateGroup(String id, long revision, GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback) {
        AppError error = IdentityValidation.group(draft);
        return error == null ? groups.updateGroup(id, revision, new GroupDraft(draft.name().trim(), draft.description()), requestId, callback) : invalid(error, callback);
    }
    @Override public AsyncOperation deleteGroup(String id, long revision, String requestId, Callback<MutationResult<GroupDeletion>> callback) { return groups.deleteGroup(id, revision, requestId, callback); }
}
