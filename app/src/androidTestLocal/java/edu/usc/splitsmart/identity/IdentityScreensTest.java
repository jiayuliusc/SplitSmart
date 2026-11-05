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
        capture("screen-timeout");
        StringBuilder details = new StringBuilder();
        instrumentation.runOnMainSync(() -> {
            for (Activity current : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                details.append(current.getClass().getSimpleName());
                TextView status = current.findViewById(R.id.feature_message);
                if (status != null) details.append(": ").append(status.getText());
            }
        });
        fail("Timed out: " + description + " [" + details + "]");
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
        await("message " + contains, () -> {
            TextView status = activity.findViewById(R.id.feature_message);
            TextView banner = activity.findViewById(R.id.feature_error_banner);
            return status.getText().toString().contains(contains)
                || (banner != null && banner.isShown() && banner.getText().toString().contains(contains));
        });
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
            input(R.id.identity_email, "missing-" + suffix + "@usc.edu");
            input(R.id.identity_password, "incorrect-password"); click(R.id.identity_submit);
            message("Check your email and password"); capture("login-error-banner");
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
            input(R.id.groups_email, "missing-member-" + suffix + "@usc.edu"); click(R.id.groups_add);
            message("No completed SplitSmart account"); capture("member-error-banner");
            input(R.id.groups_name, "Updated screen group"); input(R.id.groups_description, "Unsaved description");
            Activity beforeRotation = activity;
            instrumentation.runOnMainSync(() -> { activity.findViewById(R.id.groups_description).requestFocus(); activity.recreate(); });
            await("new detail activity", () -> !ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(beforeRotation));
            screen(GroupDetailScreen.class);
            await("refreshed detail ready", () -> activity.findViewById(R.id.groups_save).isEnabled());
            instrumentation.runOnMainSync(() -> {
                assertEquals("Updated screen group", ((TextView) activity.findViewById(R.id.groups_name)).getText().toString());
                assertEquals("Unsaved description", ((TextView) activity.findViewById(R.id.groups_description)).getText().toString());
            });
            click(R.id.groups_save);
            await("updated group title", () -> ((TextView) activity.findViewById(R.id.feature_title)).getText().toString().equals("Updated screen group")
                && activity.findViewById(R.id.feature_progress).getVisibility() != View.VISIBLE);
            click(R.id.feature_back); screen(GroupListScreen.class);
            click(R.id.groups_profile); screen(ProfileScreen.class);
            input(R.id.identity_name, "Updated screen owner");
            Activity profileBeforeRotation = activity;
            instrumentation.runOnMainSync(() -> { activity.findViewById(R.id.identity_name).clearFocus(); activity.recreate(); });
            await("new profile activity", () -> !ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(profileBeforeRotation));
            screen(ProfileScreen.class);
            instrumentation.runOnMainSync(() -> assertEquals("Updated screen owner", ((TextView) activity.findViewById(R.id.identity_name)).getText().toString()));
            click(R.id.identity_submit); message("Name updated.");
            click(R.id.identity_logout); screen(LoginScreen.class);
            click(R.id.identity_reset_link); screen(PasswordResetScreen.class);
            input(R.id.identity_email, "bad@sub.usc.edu"); click(R.id.identity_submit); message("usc.edu");
            input(R.id.identity_email, email); click(R.id.identity_submit); message("reset link has been requested"); capture("password-reset");
            input(R.id.identity_email, "missing-" + suffix + "@usc.edu"); click(R.id.identity_submit); message("reset link has been requested");
            click(R.id.feature_back); screen(LoginScreen.class);
            input(R.id.identity_email, email); input(R.id.identity_password, "incorrect-password");
            click(R.id.identity_submit); message("Check your email and password");
            input(R.id.identity_password, "screen-password"); click(R.id.identity_submit); screen(GroupListScreen.class);
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
