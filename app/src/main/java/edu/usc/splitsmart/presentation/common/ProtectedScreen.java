package edu.usc.splitsmart.presentation.common;

import android.content.Intent;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.User;
import edu.usc.splitsmart.presentation.identity.CompleteProfileScreen;

/** Private content stays hidden until session and fresh profile readiness are established. */
public abstract class ProtectedScreen extends FeatureScreen {
    protected SessionViewModel session;
    protected String currentUid;
    private boolean redirecting;
    protected void protect() {
        session = new ViewModelProvider(this, new FeatureFactory(this, null,
            state -> new SessionViewModel(services().auth()))).get(SessionViewModel.class);
        findViewById(R.id.access_retry).setOnClickListener(v -> session.refresh());
        findViewById(R.id.access_logout).setOnClickListener(v -> session.signOut());
        session.access.observe(this, access -> {
            show(R.id.access_gate, access.user() == null); show(R.id.feature_content, access.user() != null);
            if (access.signedOut()) { onAccessLost(); routeAccess(access); }
            else if (access.incomplete()) {
                onAccessLost(); routeAccess(access);
            } else if (access.user() != null) {
                if (currentUid != null && !currentUid.equals(access.user().uid())) onAccessLost();
                currentUid = access.user().uid(); onAuthorized(access.user());
            } else { onAccessLost(); text(R.id.access_status, access.error() == null ? getString(R.string.identity_checking) : access.error()); }
        });
        getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_RESUME) {
                routeAccess(session.access.getValue());
                if (!redirecting) session.refresh();
            }
        });
    }
    protected abstract void onAuthorized(User user);
    protected abstract void onAccessLost();
    private void routeAccess(SessionViewModel.Access access) {
        // A paused screen remains STARTED during transitions. It must not redirect over a new flow.
        if (access == null || redirecting || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;
        if (access.signedOut()) { redirecting = true; login(); }
        else if (access.incomplete()) {
            redirecting = true;
            startActivity(new Intent(this, CompleteProfileScreen.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
        }
    }
}
