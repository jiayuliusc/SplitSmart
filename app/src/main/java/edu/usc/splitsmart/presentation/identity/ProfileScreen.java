package edu.usc.splitsmart.presentation.identity;

import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.User;
import edu.usc.splitsmart.presentation.common.*;

public final class ProfileScreen extends ProtectedScreen {
    private ProfileViewModel model;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_identity_profile);
        model = new ViewModelProvider(this, new FeatureFactory(this, null,
            saved -> new ProfileViewModel(saved, services().auth()))).get(ProfileViewModel.class);
        bind(model, R.id.identity_submit);
        bindDraft(model, R.id.identity_name, "profileName");
        model.user.observe(this, user -> {
            if (user == null) {
                text(R.id.identity_profile_email, ""); text(R.id.identity_profile_id, ""); text(R.id.identity_name, ""); return;
            }
            text(R.id.identity_profile_email, user.email()); text(R.id.identity_profile_id, user.uscId());
            text(R.id.identity_name, model.draft("profileName", user.name()));
        });
        findViewById(R.id.identity_submit).setOnClickListener(v -> model.save(input(R.id.identity_name)));
        findViewById(R.id.identity_retry).setOnClickListener(v -> model.save(input(R.id.identity_name)));
        findViewById(R.id.identity_logout).setOnClickListener(v -> session.signOut()); protect();
    }
    @Override protected void onAuthorized(User user) { model.load(user); }
    @Override protected void onAccessLost() { if (model != null) { model.cancelOperations(); model.user.setValue(null); } }
}
