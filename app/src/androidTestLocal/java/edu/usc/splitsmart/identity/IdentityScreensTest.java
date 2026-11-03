package edu.usc.splitsmart.identity;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.SplitSmartApplication;
import edu.usc.splitsmart.presentation.groups.*;
import edu.usc.splitsmart.presentation.identity.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises real XML controls, lifecycle/navigation, ViewModels, adapters and local backend together. */
@RunWith(AndroidJUnit4.class)
public final class IdentityScreensTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity activity;
    private void await(String description, BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < end) {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            Thread.sleep(50);
        }
        fail("Timed out: " + description);
    }
    private void screen(Class<? extends Activity> type) throws Exception {
        await(type.getSimpleName(), () -> {
            for (Activity current : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (current.getClass() == type && current.findViewById(R.id.feature_content).isShown()) {
                    activity = current;
                    return current.findViewById(R.id.feature_progress).getVisibility() != View.VISIBLE;
                }
            }
            return false;
        });
    }
    private void click(int id) throws Exception {
        await("enabled button " + id, () -> activity.findViewById(id).isEnabled());
        instrumentation.runOnMainSync(() -> assertTrue(activity.findViewById(id).performClick()));
    }
    private void input(int id, String value) {
        instrumentation.runOnMainSync(() -> ((TextView) activity.findViewById(id)).setText(value));
    }
    private void message(String contains) throws Exception {
        await("message " + contains, () -> ((TextView) activity.findViewById(R.id.feature_message)).getText().toString().contains(contains));
    }
    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().waitForIdle(150, 5000);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File file = new File(instrumentation.getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        bitmap.recycle();
    }
    @Test public void registrationRecoveryGroupsProfileLoginAndResetScreens() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "ui-" + suffix + "@usc.edu";
        String otherEmail = "ui-recovery-" + suffix + "@usc.edu";
        String uscId = String.format(Locale.ROOT, "%010d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000_000_000L));
        String otherId = String.format(Locale.ROOT, "%010d", (Long.parseLong(uscId) + 1) % 10_000_000_000L);
        SplitSmartApplication app = (SplitSmartApplication) instrumentation.getTargetContext().getApplicationContext();
        instrumentation.runOnMainSync(() -> app.getContainer().getFirebaseClients().getAuth().signOut());
        try {
            instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), LoginScreen.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            screen(LoginScreen.class); capture("login");
            click(R.id.identity_register_link); screen(RegisterScreen.class);
            input(R.id.identity_name, "Screen owner"); input(R.id.identity_email, email);
            input(R.id.identity_usc_id, uscId); input(R.id.identity_password, "screen-password");
            click(R.id.identity_submit); screen(GroupListScreen.class);
            click(R.id.groups_create); screen(CreateGroupScreen.class);
            input(R.id.groups_name, "Screen group"); input(R.id.groups_description, "Roommates");
            click(R.id.groups_create); screen(GroupListScreen.class);
            await("created group row", () -> ((ViewGroup) activity.findViewById(R.id.groups_rows)).getChildCount() == 1);
            capture("groups");
            instrumentation.runOnMainSync(() -> ((ViewGroup) activity.findViewById(R.id.groups_rows)).getChildAt(0).performClick());
            screen(GroupDetailScreen.class); capture("group-details");
            input(R.id.groups_name, "Updated screen group"); click(R.id.groups_save);
            await("updated group title", () -> ((TextView) activity.findViewById(R.id.feature_title)).getText().toString().equals("Updated screen group")
                && activity.findViewById(R.id.feature_progress).getVisibility() != View.VISIBLE);
            click(R.id.feature_back); screen(GroupListScreen.class);
            click(R.id.groups_profile); screen(ProfileScreen.class);
            input(R.id.identity_name, "Updated screen owner"); click(R.id.identity_submit); message("Name updated.");
            click(R.id.identity_logout); screen(LoginScreen.class);
            click(R.id.identity_reset_link); screen(PasswordResetScreen.class);
            input(R.id.identity_email, "bad@sub.usc.edu"); click(R.id.identity_submit); message("usc.edu");
            input(R.id.identity_email, email); click(R.id.identity_submit); message("reset link has been requested"); capture("password-reset");
            click(R.id.feature_back); screen(LoginScreen.class);
            input(R.id.identity_email, email); input(R.id.identity_password, "screen-password"); click(R.id.identity_submit); screen(GroupListScreen.class);
            click(R.id.identity_logout); screen(LoginScreen.class);
            click(R.id.identity_register_link); screen(RegisterScreen.class);
            input(R.id.identity_name, "Recoverable account"); input(R.id.identity_email, otherEmail);
            input(R.id.identity_usc_id, uscId); input(R.id.identity_password, "screen-password"); click(R.id.identity_submit);
            message("USC ID is already registered");
            await("profile-only recovery", () -> ((TextView) activity.findViewById(R.id.feature_title)).getText().toString().equals("Finish your profile"));
            capture("registration-recovery");
            input(R.id.identity_usc_id, otherId); click(R.id.identity_submit); screen(GroupListScreen.class);
            click(R.id.groups_profile); screen(ProfileScreen.class);
            await("recovered private ID", () -> ((TextView) activity.findViewById(R.id.identity_profile_id)).getText().toString().equals(otherId));
        } finally {
            instrumentation.runOnMainSync(() -> {
                app.getContainer().getFirebaseClients().getAuth().signOut();
                for (Activity current : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))) current.finish();
            });
        }
    }
}
