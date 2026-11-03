package edu.usc.splitsmart.presentation.identity;

import androidx.lifecycle.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.AuthService;

public final class ProfileViewModel extends FeatureViewModel {
    private final AuthService auth;
    public final MutableLiveData<User> user = new MutableLiveData<>();
    public ProfileViewModel(SavedStateHandle state, AuthService auth) { super(state); this.auth = auth; }
    public void load(User value) { bindUser(value.uid()); user.setValue(value); }
    public void save(String name) {
        if (isBusy()) return;
        String key = intent("name", name); String retainedName = values().get(0); start();
        track(auth.updateName(retainedName, key, new Callback<>() {
            @Override public void onSuccess(MutationResult<User> result) { user.setValue(result.value()); done("Name updated."); }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }
}
