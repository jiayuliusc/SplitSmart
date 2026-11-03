package edu.usc.splitsmart.presentation.identity;

import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.presentation.common.*;

public final class PasswordResetScreen extends FeatureScreen {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_identity_reset);
        AuthViewModel model = new ViewModelProvider(this, new FeatureFactory(this, null,
            saved -> new AuthViewModel(saved, services().auth()))).get(AuthViewModel.class);
        bind(model, R.id.identity_submit);
        findViewById(R.id.identity_submit).setOnClickListener(v -> model.reset(input(R.id.identity_email)));
    }
}
