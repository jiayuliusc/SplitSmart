package edu.usc.splitsmart.service.identity;

import java.util.concurrent.Executor;
import edu.usc.splitsmart.data.repository.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.domain.validation.IdentityValidation;
import edu.usc.splitsmart.service.AuthService;
import edu.usc.splitsmart.util.PendingOperation;

public final class DefaultAuthService implements AuthService {
    private final AuthRepository auth;
    private final UserRepository users;
    private final Executor delivery;
    public DefaultAuthService(AuthRepository auth, UserRepository users, Executor delivery) {
        this.auth = auth; this.users = users; this.delivery = delivery;
    }
    private <T> AsyncOperation invalid(AppError error, Callback<T> callback) {
        PendingOperation<T> op = new PendingOperation<>(delivery, callback); op.onError(error); return op;
    }
    @Override public AsyncOperation getSession(Callback<Session> callback) { return auth.getSession(callback); }
    @Override public AsyncOperation observeSession(SessionObserver observer) { return auth.observeSession(observer); }
    @Override public AsyncOperation register(String email, String password, ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback) {
        AppError error = IdentityValidation.email(email);
        if (error == null) error = IdentityValidation.profile(profile);
        if (error == null) error = IdentityValidation.password(password);
        if (error != null) return invalid(error, callback);
        PendingOperation<MutationResult<User>> op = new PendingOperation<>(delivery, callback);
        op.attach(auth.signUp(IdentityValidation.normalizeEmail(email), password, new Callback<>() {
            @Override public void onSuccess(Session session) {
                if (!op.isCancelled()) op.attach(completeProfile(profile, requestId, op));
            }
            @Override public void onError(AppError error) { op.onError(error); }
        }));
        return op;
    }
    @Override public AsyncOperation signIn(String email, String password, Callback<Session> callback) {
        AppError error = IdentityValidation.email(email);
        if (error == null && (password == null || password.isEmpty())) error = IdentityValidation.invalid("Enter your password.", "password");
        return error == null ? auth.signIn(IdentityValidation.normalizeEmail(email), password, callback) : invalid(error, callback);
    }
    @Override public AsyncOperation signOut(Callback<Void> callback) { return auth.signOut(callback); }
    @Override public AsyncOperation sendPasswordResetEmail(String email, Callback<Void> callback) {
        AppError error = IdentityValidation.email(email);
        return error == null ? auth.sendPasswordResetEmail(IdentityValidation.normalizeEmail(email), callback) : invalid(error, callback);
    }
    @Override public AsyncOperation getCurrentProfile(ReadPolicy policy, Callback<ReadResult<User>> callback) { return users.getCurrentProfile(policy, callback); }
    @Override public AsyncOperation completeProfile(ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback) {
        AppError error = IdentityValidation.profile(profile);
        return error == null ? users.completeProfile(new ProfileDraft(profile.name().trim(), profile.uscId()), requestId, callback) : invalid(error, callback);
    }
    @Override public AsyncOperation updateName(String name, String requestId, Callback<MutationResult<User>> callback) {
        AppError error = IdentityValidation.name(name);
        return error == null ? users.updateName(name.trim(), requestId, callback) : invalid(error, callback);
    }
}
