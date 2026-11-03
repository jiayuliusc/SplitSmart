package edu.usc.splitsmart.data.firebase;

import java.util.*;
import com.google.firebase.firestore.Source;
import edu.usc.splitsmart.data.repository.GroupRepository;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.util.PendingOperation;

public final class FirebaseGroupRepository implements GroupRepository {
    private final FirebaseRepositoryContext context;
    public FirebaseGroupRepository(FirebaseRepositoryContext context) { this.context = context; }
    @Override public AsyncOperation getGroupsForCurrentUser(ReadPolicy policy, Callback<ReadResult<List<Group>>> callback) {
        PendingOperation<ReadResult<List<Group>>> op = context.operation(callback);
        String uid = context.scope.uid();
        if (uid == null || context.cached("groups", policy, op)) return op;
        Callback<ReadResult<List<Group>>> result = context.cache("groups", op);
        context.db.collection("groups").whereArrayContains("memberIds", uid).whereEqualTo("status", "ACTIVE")
            .get(Source.SERVER).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) result.onError(FirebaseErrors.map(task.getException()));
                else try {
                    List<Group> groups = new ArrayList<>();
                    task.getResult().getDocuments().forEach(doc -> groups.add(IdentityCodec.group(doc)));
                    groups.sort(Comparator.comparing(Group::groupId)); result.onSuccess(new ReadResult<>(List.copyOf(groups), ReadSource.SERVER));
                } catch (RuntimeException invalid) { result.onError(FirebaseErrors.error(ErrorCode.INTERNAL, "Unsupported group data.")); }
            }); return op;
    }
    @Override public AsyncOperation getGroup(String groupId, ReadPolicy policy, Callback<ReadResult<Group>> callback) {
        PendingOperation<ReadResult<Group>> op = context.operation(callback);
        if (context.scope.uid() == null || context.cached("group:" + groupId, policy, op)) return op;
        Callback<ReadResult<Group>> result = context.cache("group:" + groupId, op);
        context.db.collection("groups").document(groupId).get(Source.SERVER).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) result.onError(FirebaseErrors.map(task.getException()));
            else if (!task.getResult().exists()) result.onError(FirebaseErrors.error(ErrorCode.NOT_FOUND, "This group no longer exists."));
            else try { result.onSuccess(new ReadResult<>(IdentityCodec.group(task.getResult()), ReadSource.SERVER)); }
            catch (RuntimeException invalid) { result.onError(FirebaseErrors.error(ErrorCode.INTERNAL, "Unsupported group data.")); }
        }); return op;
    }
    @Override public AsyncOperation getMemberProfiles(String groupId, ReadPolicy policy, Callback<ReadResult<List<MemberProfile>>> callback) {
        PendingOperation<ReadResult<List<MemberProfile>>> op = context.operation(callback);
        if (context.scope.uid() == null || context.cached("members:" + groupId, policy, op)) return op;
        op.attach(context.call("getGroupMemberProfiles", Map.of("schemaVersion", 1, "groupId", groupId),
            value -> new ReadResult<>(IdentityCodec.members(value), ReadSource.SERVER), context.cache("members:" + groupId, op)));
        return op;
    }
    private Map<String, Object> request(String groupId, long revision, String requestId) {
        Map<String, Object> result = new HashMap<>(); result.put("schemaVersion", 1); result.put("requestId", requestId);
        result.put("groupId", groupId); result.put("expectedRevision", Long.toString(revision)); return result;
    }
    private AsyncOperation groupCall(String name, Map<String, Object> data, String requestId, Callback<MutationResult<Group>> callback) {
        return context.call(name, data, value -> context.mutation(value, requestId, true, IdentityCodec::group), callback);
    }
    @Override public AsyncOperation createGroup(GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback) {
        return groupCall("createGroup", Map.of("schemaVersion", 1, "requestId", requestId, "draft", IdentityCodec.draft(draft)), requestId, callback);
    }
    @Override public AsyncOperation addMember(String groupId, long revision, String email, String requestId, Callback<MutationResult<Group>> callback) {
        Map<String, Object> data = request(groupId, revision, requestId); data.put("email", email);
        return groupCall("addGroupMember", data, requestId, callback);
    }
    @Override public AsyncOperation updateGroup(String groupId, long revision, GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback) {
        Map<String, Object> data = request(groupId, revision, requestId); data.put("draft", IdentityCodec.draft(draft));
        return groupCall("updateGroup", data, requestId, callback);
    }
    @Override public AsyncOperation deleteGroup(String groupId, long revision, String requestId, Callback<MutationResult<GroupDeletion>> callback) {
        return context.call("deleteGroup", request(groupId, revision, requestId), value -> context.mutation(value, requestId, true, result -> {
            Map<?, ?> data = IdentityCodec.map(result);
            if (!"DELETING".equals(IdentityCodec.string(data, "status"))) throw new IllegalArgumentException("Invalid deletion result");
            return new GroupDeletion(IdentityCodec.string(data, "groupId"), GroupStatus.DELETING);
        }), callback);
    }
}
