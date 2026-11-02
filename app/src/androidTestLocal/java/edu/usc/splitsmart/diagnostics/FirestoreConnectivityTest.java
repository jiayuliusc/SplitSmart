package edu.usc.splitsmart.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Source;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import edu.usc.splitsmart.BuildConfig;
import edu.usc.splitsmart.config.FirebaseClients;
import edu.usc.splitsmart.config.SplitSmartApplication;

/** Local test APK only. Reads a synthetic server record without signing in. */
@RunWith(AndroidJUnit4.class)
public final class FirestoreConnectivityTest {
    @Test
    public void readsDiagnosticFixtureFromEmulator() throws Exception {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATORS);
        assertEquals("demo-splitsmart", BuildConfig.FIREBASE_PROJECT_ID);
        SplitSmartApplication app = (SplitSmartApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext();
        FirebaseClients clients = app.getContainer().getFirebaseClients();
        assertNull("The diagnostic must not require an auth feature", clients.getAuth().getCurrentUser());

        DocumentSnapshot record = Tasks.await(clients.getFirestore()
                .document("_diagnostics/connectivity").get(Source.SERVER), 20, TimeUnit.SECONDS);

        assertTrue("Run npm run diagnostics:seed first", record.exists());
        assertFalse(record.getMetadata().isFromCache());
        assertEquals("SplitSmart emulator connection OK", record.getString("message"));
        assertEquals("demo-splitsmart", record.getString("projectId"));
        assertNotNull(record.getLong("schemaVersion"));
        assertEquals(Long.valueOf(1), record.getLong("schemaVersion"));
        Log.i("SplitSmartDiagnostic", "PASS: Android read _diagnostics/connectivity from demo-splitsmart (server)");
    }
}
