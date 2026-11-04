package edu.usc.splitsmart.domain.contract;

/** Revision invalidations only, never financial data. Callbacks are queued; error is terminal. */
public interface GroupRevisionObserver {
    void onRevision(long revision);
    void onError(AppError error);
}
