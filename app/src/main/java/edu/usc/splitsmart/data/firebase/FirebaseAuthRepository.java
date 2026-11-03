package edu.usc.splitsmart.data.firebase;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import edu.usc.splitsmart.data.repository.AuthRepository;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.Session;
import edu.usc.splitsmart.util.PendingOperation;

public final class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth auth;
    private final Executor delivery;
    public FirebaseAuthRepository(FirebaseAuth auth, Executor delivery) { this.auth = auth; this.delivery = delivery; }
    private Session session() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? new Session(null, null) : new Session(user.getUid(), user.getEmail());
    }
    @Override public AsyncOperation getSession(Callback<Session> callback) {
        PendingOperation<Session> op = new PendingOperation<>(delivery, callback); op.onSuccess(session()); return op;
    }
    @Override public AsyncOperation observeSession(SessionObserver observer) {
        AtomicBoolean cancelled = new AtomicBoolean();
        Object lock = new Object();
        FirebaseAuth.AuthStateListener listener = ignored -> {
            Session value = session();
            delivery.execute(() -> { synchronized (lock) { if (!cancelled.get()) observer.onSessionChanged(value); } });
        };
        auth.addAuthStateListener(listener); // Firebase queues the initial state and subsequent changes.
        return () -> { synchronized (lock) { cancelled.set(true); auth.removeAuthStateListener(listener); } };
    }
    @Override public AsyncOperation signUp(String email, String password, Callback<Session> callback) {
        PendingOperation<Session> op = new PendingOperation<>(delivery, callback);
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) op.onSuccess(session()); else op.onError(FirebaseErrors.map(task.getException()));
        }); return op;
    }
    @Override public AsyncOperation signIn(String email, String password, Callback<Session> callback) {
        PendingOperation<Session> op = new PendingOperation<>(delivery, callback);
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) op.onSuccess(session()); else op.onError(FirebaseErrors.map(task.getException()));
        }); return op;
    }
    @Override public AsyncOperation signOut(Callback<Void> callback) {
        PendingOperation<Void> op = new PendingOperation<>(delivery, callback);
        auth.signOut(); op.onSuccess(null); return op;
    }
    @Override public AsyncOperation sendPasswordResetEmail(String email, Callback<Void> callback) {
        PendingOperation<Void> op = new PendingOperation<>(delivery, callback);
        auth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            // Match Firebase's enumeration-protected behavior in emulators and older projects too.
            if (task.isSuccessful() || task.getException() instanceof FirebaseAuthException failure &&
                "ERROR_USER_NOT_FOUND".equals(failure.getErrorCode())) op.onSuccess(null);
            else {
                AppError error = FirebaseErrors.map(task.getException());
                op.onError(error.code() == ErrorCode.UNAUTHENTICATED ? new AppError(error.code(),
                    "Could not request a password reset. Please try again later.", null, error.retryable()) : error);
            }
        }); return op;
    }
}
