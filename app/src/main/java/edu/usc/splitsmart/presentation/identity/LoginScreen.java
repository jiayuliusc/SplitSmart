package edu.usc.splitsmart.presentation.identity;

import android.content.Intent;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.groups.GroupListScreen;

public final class LoginScreen extends FeatureScreen {
    private AuthViewModel model;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_identity_login); show(R.id.feature_back, false);
        model = new ViewModelProvider(this, new FeatureFactory(this, null, saved -> new AuthViewModel(saved, services().auth()))).get(AuthViewModel.class);
        bind(model, R.id.identity_submit, R.id.identity_reset_link, R.id.identity_register_link, R.id.identity_retry_access);
        findViewById(R.id.identity_submit).setOnClickListener(v -> model.login(input(R.id.identity_email), input(R.id.identity_password)));
        findViewById(R.id.identity_reset_link).setOnClickListener(v -> startActivity(new Intent(this, PasswordResetScreen.class)));
        findViewById(R.id.identity_register_link).setOnClickListener(v -> startActivity(new Intent(this, RegisterScreen.class)));
        findViewById(R.id.identity_retry_access).setOnClickListener(v -> model.checkSession());
        model.destination.observe(this, destination -> {
            if (destination == null || destination == AuthViewModel.Destination.LOGIN) return;
            Class<?> target = destination == AuthViewModel.Destination.GROUPS ? GroupListScreen.class : CompleteProfileScreen.class;
            model.destination.setValue(null);
            startActivity(new Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
        });
    }
    @Override protected void onResume() { super.onResume(); model.checkSession(); }
    @Override protected void onPause() { super.onPause(); text(R.id.identity_password, ""); }
}
