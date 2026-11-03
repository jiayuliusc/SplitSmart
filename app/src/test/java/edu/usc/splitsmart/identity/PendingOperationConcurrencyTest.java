package edu.usc.splitsmart.identity;

import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.util.PendingOperation;

public final class PendingOperationConcurrencyTest {
    @Test public void parentCancellationDoesNotDeadlockAChildConsultingItsParent() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), cancelling = new CountDownLatch(1);
        CountDownLatch callbackFinished = new CountDownLatch(1), cancelFinished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Executor threads = command -> { Thread thread = new Thread(command); thread.setDaemon(true); thread.start(); };
        Callback<Void> unused = new Callback<>() {
            @Override public void onSuccess(Void value) { fail("Parent callback should not run"); }
            @Override public void onError(AppError error) { fail("Parent callback should not run"); }
        };
        PendingOperation<Void> parent = new PendingOperation<>(threads, unused);
        PendingOperation<Void> child = new PendingOperation<>(threads, new Callback<>() {
            @Override public void onSuccess(Void value) {
                entered.countDown();
                try {
                    assertTrue(cancelling.await(2, TimeUnit.SECONDS));
                    assertTrue(parent.isCancelled());
                } catch (Throwable error) { failure.set(error); }
                finally { callbackFinished.countDown(); }
            }
            @Override public void onError(AppError error) { fail("Unexpected error"); }
        });
        parent.attach(cancelling::countDown);
        parent.attach(child);
        child.onSuccess(null);
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        threads.execute(() -> { parent.cancel(); cancelFinished.countDown(); });
        assertTrue("Cancellation deadlocked with a running child", cancelFinished.await(2, TimeUnit.SECONDS));
        assertTrue(callbackFinished.await(2, TimeUnit.SECONDS));
        assertNull(failure.get());
    }
}
