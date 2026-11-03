package edu.usc.splitsmart.domain.contract;

/** Cancel suppresses future callback delivery; it does not roll back a remote commit. */
public interface AsyncOperation {
    void cancel();
}
