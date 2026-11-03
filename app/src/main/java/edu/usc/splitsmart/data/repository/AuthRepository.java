package edu.usc.splitsmart.data.repository;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;

/** Contract v1; threading, ordering, reads and retries are specified in docs/CONTRACTS.md. */
public interface AuthRepository {
    AsyncOperation getSession(Callback<Session> callback);

    AsyncOperation observeSession(SessionObserver observer);

    AsyncOperation signUp(String email, String password, Callback<Session> callback);

    AsyncOperation signIn(String email, String password, Callback<Session> callback);

    AsyncOperation signOut(Callback<Void> callback);

    /** X01: success acknowledges a reset request, not mailbox delivery or account existence. */
    AsyncOperation sendPasswordResetEmail(String email, Callback<Void> callback);
}
