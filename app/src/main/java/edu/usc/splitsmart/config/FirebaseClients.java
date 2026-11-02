package edu.usc.splitsmart.config;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.google.firebase.functions.FirebaseFunctions;

import edu.usc.splitsmart.BuildConfig;

/** Owns SDK setup. Instances are exposed only after emulator routing is complete. */
public final class FirebaseClients {
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseFunctions functions;

    FirebaseClients(Context context) {
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(context, options);
        auth = FirebaseAuth.getInstance(app);
        firestore = FirebaseFirestore.getInstance(app);
        functions = FirebaseFunctions.getInstance(app, BuildConfig.FUNCTIONS_REGION);

        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            // Android Virtual Device alias for the development machine's loopback interface.
            String host = "10.0.2.2";
            auth.useEmulator(host, 9099);
            firestore.useEmulator(host, 8080);
            functions.useEmulator(host, 5001);
            // Avoid stale disk data after resetting the local emulator suite.
            firestore.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build());
        }
    }

    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    public FirebaseFunctions getFunctions() {
        return functions;
    }
}
