package edu.usc.splitsmart.presentation.identity;

import androidx.lifecycle.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.AuthService;

public final class AuthViewModel extends FeatureViewModel {
    public enum Destination { LOGIN, COMPLETE_PROFILE, GROUPS }
    private final AuthService auth;
    public final MutableLiveData<Destination> destination = new MutableLiveData<>();
    public final MutableLiveData<Boolean> incomplete = new MutableLiveData<>(false);
    public AuthViewModel(SavedStateHandle state, AuthService auth) { super(state); this.auth = auth; }
    public void checkSession() {
        if (isBusy()) return;
        start(); track(auth.getSession(new Callback<>() {
            @Override public void onSuccess(Session session) {
                if (session.uid() == null) { busy.setValue(false); destination.setValue(Destination.LOGIN); }
                else { bindUser(session.uid()); checkProfile(); }
            }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }
    private void checkProfile() {
        track(auth.getCurrentProfile(ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<User> user) { done(""); destination.setValue(Destination.GROUPS); }
            @Override public void onError(AppError e) {
                busy.setValue(false);
                if (e.code() == ErrorCode.NOT_FOUND) destination.setValue(Destination.COMPLETE_PROFILE); else failed(e);
            }
        }));
    }
    public void login(String email, String password) {
        if (isBusy()) return; start();
        track(auth.signIn(email, password, new Callback<>() {
            @Override public void onSuccess(Session session) { bindUser(session.uid()); checkProfile(); }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }
    public void register(String name, String email, String uscId, String password, boolean completionOnly) {
        if (isBusy()) return;
        if (pending()) { retryProfile(); return; }
        saved.set("registrationName", name); saved.set("registrationId", uscId);
        String key = intent("profile", name, uscId); start();
        ProfileDraft draft = new ProfileDraft(name, uscId);
        if (completionOnly || Boolean.TRUE.equals(incomplete.getValue())) {
            track(auth.completeProfile(draft, key, profileCallback()));
        } else track(auth.register(email, password, draft, key, profileCallback()));
    }
    private Callback<MutationResult<User>> profileCallback() {
        return new Callback<>() {
            @Override public void onSuccess(MutationResult<User> result) {
                bindUser(result.value().uid()); done(""); destination.setValue(Destination.GROUPS);
            }
            @Override public void onError(AppError failure) {
                // Auth success is retained; only profile completion is retried, never account creation.
                track(auth.getSession(new Callback<>() {
                    @Override public void onSuccess(Session session) {
                        incomplete.setValue(session.uid() != null);
                        if (session.uid() != null) bindUser(session.uid()); else resolved();
                        failed(failure);
                    }
                    @Override public void onError(AppError e) { failed(failure); }
                }));
            }
        };
    }
    public void retryProfile() {
        if (!pending() || isBusy()) return;
        start(); track(auth.completeProfile(new ProfileDraft(values().get(0), values().get(1)), requestId(), profileCallback()));
    }
    public void reset(String email) {
        if (isBusy()) return; start();
        track(auth.sendPasswordResetEmail(email, new Callback<>() {
            @Override public void onSuccess(Void ignored) { done("If an account matches this USC email, a reset link has been requested. Check your inbox and spam folder."); }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }
    public void logout() {
        if (isBusy()) return; start();
        track(auth.signOut(new Callback<>() {
            @Override public void onSuccess(Void ignored) { done(""); destination.setValue(Destination.LOGIN); }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }
    public String profileName() { String value = saved.get("registrationName"); return value == null ? "" : value; }
    public String profileId() { String value = saved.get("registrationId"); return value == null ? "" : value; }
}
