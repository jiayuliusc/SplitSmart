package edu.usc.splitsmart.config;

import android.content.Context;

/** Manual dependency wiring; add agreed repository/service instances as features arrive. */
public final class AppContainer {
    private final FirebaseClients firebaseClients;

    AppContainer(Context context) {
        firebaseClients = new FirebaseClients(context);
    }

    /** Infrastructure seam for repository construction and the local instrumentation check. */
    public FirebaseClients getFirebaseClients() {
        return firebaseClients;
    }
}
