package edu.usc.splitsmart.presentation.common;

import androidx.lifecycle.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.service.AuthService;

public final class SessionViewModel extends ViewModel {
    public record Access(User user, boolean signedOut, boolean incomplete, String error) { }
    public final MutableLiveData<Access> access = new MutableLiveData<>(new Access(null, false, false, null));
    private final AuthService auth;
    private AsyncOperation observer;
    private AsyncOperation read;
    private String uid;
    private boolean loading;
    public SessionViewModel(AuthService auth) {
        this.auth = auth;
        observer = auth.observeSession(new SessionObserver() {
            @Override public void onSessionChanged(Session session) {
                if (read != null) read.cancel(); loading = false; uid = session.uid();
                access.setValue(new Access(null, uid == null, false, null));
                if (uid != null) refresh();
            }
            @Override public void onError(AppError error) { access.setValue(new Access(null, false, false, error.message())); }
        });
    }
    public void refresh() {
        if (uid == null || loading) return;
        loading = true;
        read = auth.getCurrentProfile(ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<User> result) { loading = false; access.setValue(new Access(result.value(), false, false, null)); }
            @Override public void onError(AppError error) {
                loading = false; access.setValue(new Access(null, error.code() == ErrorCode.UNAUTHENTICATED,
                    error.code() == ErrorCode.NOT_FOUND, error.message()));
            }
        });
    }
    public void signOut() { if (read != null) read.cancel(); read = auth.signOut(new Callback<>() {
        @Override public void onSuccess(Void ignored) { access.setValue(new Access(null, true, false, null)); }
        @Override public void onError(AppError error) { access.setValue(new Access(null, false, false, error.message())); }
    }); }
    @Override protected void onCleared() { if (observer != null) observer.cancel(); if (read != null) read.cancel(); }
}
