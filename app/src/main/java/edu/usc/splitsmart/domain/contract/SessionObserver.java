package edu.usc.splitsmart.domain.contract;

import edu.usc.splitsmart.domain.model.Session;

/** Initial state and changes on main; error is terminal. cancel() detaches observation. */
public interface SessionObserver {
    void onSessionChanged(Session session);
    void onError(AppError error);
}
