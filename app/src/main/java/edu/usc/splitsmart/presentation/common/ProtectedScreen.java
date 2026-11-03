package edu.usc.splitsmart.presentation.common;

import android.content.Intent;
import androidx.lifecycle.ViewModelProvider;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.User;
import edu.usc.splitsmart.presentation.identity.CompleteProfileScreen;

/** Private content stays hidden until session and fresh profile readiness are established. */
public abstract class ProtectedScreen extends FeatureScreen {
    protected SessionViewModel session;
    protected String currentUid;
    protected void protect() {
        session = new ViewModelProvider(this, new FeatureFactory(this, null,
            state -> new SessionViewModel(services().auth()))).get(SessionViewModel.class);
        findViewById(R.id.access_retry).setOnClickListener(v -> session.refresh());
        findViewById(R.id.access_logout).setOnClickListener(v -> session.signOut());
        session.access.observe(this, access -> {
            show(R.id.access_gate, access.user() == null); show(R.id.feature_content, access.user() != null);
            if (access.signedOut()) { onAccessLost(); login(); }
            else if (access.incomplete()) {
                onAccessLost(); startActivity(new Intent(this, CompleteProfileScreen.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
            } else if (access.user() != null) {
                if (currentUid != null && !currentUid.equals(access.user().uid())) onAccessLost();
                currentUid = access.user().uid(); onAuthorized(access.user());
            } else { onAccessLost(); text(R.id.access_status, access.error() == null ? getString(R.string.identity_checking) : access.error()); }
        });
    }
    protected abstract void onAuthorized(User user);
    protected abstract void onAccessLost();
    @Override protected void onResume() { super.onResume(); if (session != null) session.refresh(); }
}
