package edu.usc.splitsmart.domain.contract;

/** One asynchronous terminal callback on Android's main thread, unless cancelled. */
public interface Callback<T> {
    void onSuccess(T value);
    void onError(AppError error);
}
