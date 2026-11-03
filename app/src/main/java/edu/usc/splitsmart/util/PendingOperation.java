package edu.usc.splitsmart.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import edu.usc.splitsmart.domain.contract.*;

/** Queued, cancellable single delivery. Children may be attached before or after cancellation. */
public final class PendingOperation<T> implements AsyncOperation, Callback<T> {
    private final Executor delivery;
    private final Callback<T> callback;
    private final BooleanSupplier current;
    private final List<AsyncOperation> children = new ArrayList<>();
    private boolean cancelled;
    private boolean complete;

    public PendingOperation(Executor delivery, Callback<T> callback) { this(delivery, callback, () -> true); }
    public PendingOperation(Executor delivery, Callback<T> callback, BooleanSupplier current) {
        this.delivery = delivery; this.callback = callback; this.current = current;
    }
    public synchronized boolean isCancelled() { return cancelled; }
    public synchronized void attach(AsyncOperation child) {
        if (cancelled) child.cancel(); else children.add(child);
    }
    @Override public synchronized void cancel() {
        cancelled = true;
        children.forEach(AsyncOperation::cancel);
        children.clear();
    }
    private void deliver(Runnable action) {
        delivery.execute(() -> {
            synchronized (this) {
                if (cancelled || complete || !current.getAsBoolean()) return;
                complete = true;
                action.run();
            }
        });
    }
    @Override public void onSuccess(T value) { deliver(() -> callback.onSuccess(value)); }
    @Override public void onError(AppError error) { deliver(() -> callback.onError(error)); }
}
