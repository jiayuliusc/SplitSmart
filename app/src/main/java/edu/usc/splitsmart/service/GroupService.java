package edu.usc.splitsmart.service;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import java.util.List;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface GroupService {
    AsyncOperation getGroupsForCurrentUser(ReadPolicy policy, Callback<ReadResult<List<Group>>> callback);

    AsyncOperation getGroup(String groupId, ReadPolicy policy, Callback<ReadResult<Group>> callback);

    AsyncOperation getMemberProfiles(String groupId, ReadPolicy policy, Callback<ReadResult<List<MemberProfile>>> callback);

    AsyncOperation createGroup(GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback);

    AsyncOperation addMember(String groupId, long expectedRevision, String email, String requestId, Callback<MutationResult<Group>> callback);

    AsyncOperation updateGroup(String groupId, long expectedRevision, GroupDraft draft, String requestId, Callback<MutationResult<Group>> callback);

    AsyncOperation deleteGroup(String groupId, long expectedRevision, String requestId, Callback<MutationResult<GroupDeletion>> callback);
}
