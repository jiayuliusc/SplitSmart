package edu.usc.splitsmart.data.firebase;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.util.PendingOperation;

/** Shared adapter infrastructure: main-thread delivery, account scope and bounded exact-payload retries. */
public final class FirebaseRepositoryContext {
    final FirebaseFirestore db;
    final Executor delivery;
    final SessionScope scope;
    private final FirebaseFunctions functions;
    private final ScheduledExecutorService scheduler;
    public FirebaseRepositoryContext(FirebaseAuth auth, FirebaseFirestore db, FirebaseFunctions functions,
            Executor delivery, ScheduledExecutorService scheduler) {
        this.db = db; this.functions = functions; this.delivery = delivery; this.scheduler = scheduler;
        scope = new SessionScope(auth);
    }
    <T> PendingOperation<T> operation(Callback<T> callback) {
        String uid = scope.uid(); long generation = scope.generation();
        PendingOperation<T> op = new PendingOperation<>(delivery, callback, () -> scope.current(uid, generation));
        if (uid == null) op.onError(FirebaseErrors.error(ErrorCode.UNAUTHENTICATED, "Sign in to continue."));
        return op;
    }
    <T> AsyncOperation call(String name, Map<String, Object> payload, Function<Object, T> decode, Callback<T> callback) {
        PendingOperation<T> op = operation(callback);
        String uid = scope.uid(); long epoch = scope.generation();
        if (uid != null) attempt(name, payload, decode, op, uid, epoch, 0);
        return op;
    }
    private <T> void attempt(String name, Map<String, Object> payload, Function<Object, T> decode,
            PendingOperation<T> op, String uid, long epoch, int retry) {
        if (op.isCancelled() || !scope.current(uid, epoch)) return;
        functions.getHttpsCallable(name).call(payload).addOnCompleteListener(task -> {
            if (op.isCancelled() || !scope.current(uid, epoch)) return;
            if (task.isSuccessful()) {
                try { op.onSuccess(decode.apply(task.getResult().getData())); }
                catch (RuntimeException invalid) { op.onError(FirebaseErrors.error(ErrorCode.INTERNAL, "The server returned an unsupported response.")); }
            } else {
                AppError error = FirebaseErrors.map(task.getException());
                if (error.retryable() && retry < 3) {
                    ScheduledFuture<?> pending = scheduler.schedule(() -> delivery.execute(() ->
                        attempt(name, payload, decode, op, uid, epoch, retry + 1)),
                        (250L << retry) + ThreadLocalRandom.current().nextLong(100), TimeUnit.MILLISECONDS);
                    op.attach(() -> pending.cancel(false));
                } else op.onError(error);
            }
        });
    }
    @SuppressWarnings("unchecked")
    <T> boolean cached(String key, ReadPolicy policy, PendingOperation<ReadResult<T>> op) {
        if (policy != ReadPolicy.CACHE_ONLY) return false;
        Object value = scope.cache.get(key);
        if (value == null) op.onError(FirebaseErrors.error(ErrorCode.CACHE_MISS, FirebaseErrors.message(ErrorCode.CACHE_MISS)));
        else op.onSuccess(new ReadResult<>((T) value, ReadSource.CACHE));
        return true;
    }
    <T> Callback<ReadResult<T>> cache(String key, PendingOperation<ReadResult<T>> op) {
        String uid = scope.uid(); long generation = scope.generation();
        return new Callback<>() {
            @Override public void onSuccess(ReadResult<T> value) {
                if (scope.current(uid, generation) && !op.isCancelled()) {
                    scope.cache.put(key, value.value()); op.onSuccess(value);
                }
            }
            @Override public void onError(AppError error) { op.onError(error); }
        };
    }
    <T> MutationResult<T> mutation(Object value, String requestId, boolean group, Function<Object, T> decode) {
        Map<?, ?> data = IdentityCodec.map(value); IdentityCodec.version(data);
        if (!requestId.equals(IdentityCodec.string(data, "requestId"))) throw new IllegalArgumentException("Wrong receipt");
        T result = decode.apply(data.get("value"));
        Long revision = group ? IdentityCodec.revision(data.get("groupRevision")) : null;
        scope.cache.clear();
        return new MutationResult<>(requestId, revision, result);
    }
}
