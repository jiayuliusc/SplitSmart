package edu.usc.splitsmart.presentation.identity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.groups.GroupListScreen;

public class RegisterScreen extends FeatureScreen {
    private AuthViewModel model;
    protected boolean completionOnly() { return false; }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_identity_register);
        model = new ViewModelProvider(this, new FeatureFactory(this, null, saved -> new AuthViewModel(saved, services().auth()))).get(AuthViewModel.class);
        if (state == null) { text(R.id.identity_name, model.profileName()); text(R.id.identity_usc_id, model.profileId()); }
        bind(model, R.id.identity_submit, R.id.identity_logout);
        findViewById(R.id.identity_submit).setOnClickListener(v -> model.register(input(R.id.identity_name), input(R.id.identity_email),
            input(R.id.identity_usc_id), input(R.id.identity_password), completionOnly()));
        findViewById(R.id.identity_retry).setOnClickListener(v -> model.retryProfile());
        findViewById(R.id.identity_logout).setOnClickListener(v -> model.logout());
        model.incomplete.observe(this, partial -> {
            boolean completing = completionOnly() || Boolean.TRUE.equals(partial);
            text(R.id.feature_title, getString(completing ? R.string.identity_complete_title : R.string.identity_register_title));
            text(R.id.feature_intro, getString(completing ? R.string.identity_complete_intro : R.string.identity_register_intro));
            text(R.id.identity_submit, getString(completing ? R.string.identity_complete : R.string.identity_register));
            ((View) findViewById(R.id.identity_email).getParent().getParent()).setVisibility(completing ? View.GONE : View.VISIBLE);
            ((View) findViewById(R.id.identity_password).getParent().getParent()).setVisibility(completing ? View.GONE : View.VISIBLE);
            show(R.id.identity_logout, completing);
        });
        model.destination.observe(this, destination -> {
            if (destination == AuthViewModel.Destination.GROUPS) {
                model.destination.setValue(null); startActivity(new Intent(this, GroupListScreen.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
            } else if (destination == AuthViewModel.Destination.LOGIN) login();
        });
    }
    @Override protected void onPause() { super.onPause(); text(R.id.identity_password, ""); }
}
