package edu.usc.splitsmart.config;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import edu.usc.splitsmart.data.firebase.*;
import edu.usc.splitsmart.service.AuthService;
import edu.usc.splitsmart.service.GroupService;
import edu.usc.splitsmart.service.identity.DefaultAuthService;
import edu.usc.splitsmart.service.groups.DefaultGroupService;

/** Manual dependency wiring; add agreed repository/service instances as features arrive. */
public final class AppContainer implements IdentityGroupServices {
    private final FirebaseClients firebaseClients;
    private final AuthService auth;
    private final GroupService groups;

    AppContainer(Context context) {
        firebaseClients = new FirebaseClients(context);
        Handler main = new Handler(Looper.getMainLooper());
        Executor delivery = command -> main.post(command);
        FirebaseRepositoryContext repositories = new FirebaseRepositoryContext(firebaseClients.getAuth(),
            firebaseClients.getFirestore(), firebaseClients.getFunctions(), delivery,
            Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "splitsmart-retries"); t.setDaemon(true); return t; }));
        auth = new DefaultAuthService(new FirebaseAuthRepository(firebaseClients.getAuth(), delivery),
            new FirebaseUserRepository(repositories), delivery);
        groups = new DefaultGroupService(new FirebaseGroupRepository(repositories), delivery);
    }

    /** Infrastructure seam for repository construction and the local instrumentation check. */
    public FirebaseClients getFirebaseClients() {
        return firebaseClients;
    }
    @Override public AuthService auth() { return auth; }
    @Override public GroupService groups() { return groups; }
}
