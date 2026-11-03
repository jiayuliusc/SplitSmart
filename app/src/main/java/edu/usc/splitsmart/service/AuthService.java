package edu.usc.splitsmart.service;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface AuthService {
    AsyncOperation getSession(Callback<Session> callback);

    AsyncOperation observeSession(SessionObserver observer);

    AsyncOperation register(String email, String password, ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback);

    AsyncOperation signIn(String email, String password, Callback<Session> callback);

    AsyncOperation signOut(Callback<Void> callback);

    AsyncOperation getCurrentProfile(ReadPolicy policy, Callback<ReadResult<User>> callback);

    AsyncOperation completeProfile(ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback);

    AsyncOperation updateName(String name, String requestId, Callback<MutationResult<User>> callback);
}
