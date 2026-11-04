package edu.usc.splitsmart.domain.contract;

/** Cancellable invalidation stream with a synchronous, SDK-free current-account guard. */
public interface GroupObservation extends AsyncOperation {
    boolean isCurrent(String uid);
}
