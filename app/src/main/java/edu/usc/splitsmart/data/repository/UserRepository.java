package edu.usc.splitsmart.data.repository;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface UserRepository {
    AsyncOperation getCurrentProfile(ReadPolicy policy, Callback<ReadResult<User>> callback);

    AsyncOperation completeProfile(ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback);

    AsyncOperation updateName(String name, String requestId, Callback<MutationResult<User>> callback);
}
