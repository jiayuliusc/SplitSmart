package edu.usc.splitsmart.registration;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.SplitSmartApplication;
import edu.usc.splitsmart.presentation.groups.CreateGroupScreen;
import edu.usc.splitsmart.presentation.groups.GroupDetailScreen;
import edu.usc.splitsmart.presentation.groups.GroupListScreen;
import edu.usc.splitsmart.presentation.identity.LoginScreen;
import edu.usc.splitsmart.presentation.identity.ProfileScreen;
import edu.usc.splitsmart.presentation.identity.RegisterScreen;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;

/** Real-screen black-box coverage using Espresso and the local Firebase emulators. */
@RunWith(AndroidJUnit4.class)
public final class Feature1EspressoTest {
    private final android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity activity;

    @After public void signOutAndCloseScreens() {
        instrumentation.runOnMainSync(() -> {
            application().getContainer().getFirebaseClients().getAuth().signOut();
            for (Activity current : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))) current.finish();
        });
    }

    @Test public void registrationRejectsNonUscEmailAndMalformedUscId() throws Exception {
        launchLoggedOut();
        registerScreen();
        fillRegistration("student@example.org", "Bad domain", "0123456789", "screen-password");
        tap(R.id.identity_submit);
        awaitText("exact usc.edu domain");
        fill(R.id.identity_email, email("valid"));
        fill(R.id.identity_usc_id, "123456789");
        tap(R.id.identity_submit);
        awaitText("exactly 10 digits");
        assertScreen(RegisterScreen.class);
    }

    @Test public void registrationRejectsDuplicateEmailAndDuplicateUscId() throws Exception {
        String email = email("dupe");
        String uscId = id();
        launchLoggedOut();
        register(email, "First account", uscId);
        logout();
        registerScreen();
        fillRegistration(email, "Second account", id(), "screen-password");
        tap(R.id.identity_submit);
        awaitText("already registered");
        logout();
        registerScreen();
        fillRegistration(email("dupe-id"), "Third account", uscId, "screen-password");
        tap(R.id.identity_submit);
        awaitScreen(RegisterScreen.class);
        awaitText("USC ID is already registered");
        onView(withText("Finish your profile")).check(matches(isDisplayed()));
    }

    @Test public void accountLifecycleUpdatesNameAndKeepsIdentifiersImmutable() throws Exception {
        String email = email("lifecycle");
        String uscId = id();
        launchLoggedOut();
        register(email, "Original name", uscId);
        tap(R.id.groups_profile);
        assertScreen(ProfileScreen.class);
        onView(withText(email)).check(matches(isDisplayed()));
        onView(withText(uscId)).check(matches(isDisplayed()));
        fill(R.id.identity_name, "Changed name");
        tap(R.id.identity_submit);
        awaitText("Name updated");
        onView(withText(email)).check(matches(isDisplayed()));
        onView(withText(uscId)).check(matches(isDisplayed()));
        logoutFromUi();
        login(email);
        tap(R.id.groups_profile);
        assertScreen(ProfileScreen.class);
        onView(withText("Changed name")).check(matches(isDisplayed()));
        onView(withText(email)).check(matches(isDisplayed()));
        onView(withText(uscId)).check(matches(isDisplayed()));
    }

    @Test public void ownerAddsMemberAcrossGroupsAndMemberCanViewBothMemberships() throws Exception {
        String ownerEmail = email("owner");
        String memberEmail = email("member");
        launchLoggedOut();
        register(ownerEmail, "Group owner", id());
        createGroup("Alpha group");
        createGroup("Beta group");
        logout();
        register(memberEmail, "Group member", id());
        logout();
        login(ownerEmail);
        openGroup("Alpha group");
        fill(R.id.groups_email, "unknown-" + UUID.randomUUID() + "@usc.edu");
        tap(R.id.groups_add);
        awaitText("No completed SplitSmart account");
        fill(R.id.groups_email, memberEmail);
        tap(R.id.groups_add);
        awaitText(memberEmail);
        fill(R.id.groups_email, memberEmail);
        tap(R.id.groups_add);
        awaitText("already a member");
        backToGroups();
        openGroup("Beta group");
        fill(R.id.groups_email, memberEmail);
        tap(R.id.groups_add);
        awaitText(memberEmail);
        logout();
        login(memberEmail);
        onView(withText("Alpha group")).check(matches(isDisplayed()));
        onView(withText("Beta group")).check(matches(isDisplayed()));
        openGroup("Alpha group");
        onView(withText(ownerEmail)).check(matches(isDisplayed()));
        onView(withText(memberEmail)).check(matches(isDisplayed()));
    }

    @Test public void groupPersistsAfterReopenAndOnlyOwnerGetsEditAndDeleteControls() throws Exception {
        String ownerEmail = email("persist-owner");
        String memberEmail = email("persist-member");
        launchLoggedOut();
        register(ownerEmail, "Owner", id());
        createGroup("Persistent group");
        logout();
        register(memberEmail, "Member", id());
        logout();
        login(ownerEmail);
        openGroup("Persistent group");
        fill(R.id.groups_email, memberEmail);
        tap(R.id.groups_add);
        awaitText(memberEmail);
        logout();
        login(memberEmail);
        openGroup("Persistent group");
        onView(withId(R.id.groups_owner_controls)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        logout();
        login(ownerEmail);
        openGroup("Persistent group");
        onView(withId(R.id.groups_owner_controls)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        tap(R.id.groups_delete);
        onView(withText(R.string.groups_delete)).perform(click());
        awaitScreen(GroupListScreen.class);
        awaitNoText("Persistent group");
    }

    private SplitSmartApplication application() {
        return (SplitSmartApplication) instrumentation.getTargetContext().getApplicationContext();
    }
    private void launchLoggedOut() throws Exception {
        instrumentation.runOnMainSync(() -> application().getContainer().getFirebaseClients().getAuth().signOut());
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), LoginScreen.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitScreen(LoginScreen.class);
    }
    private void registerScreen() throws Exception {
        awaitScreen(LoginScreen.class);
        tap(R.id.identity_register_link);
        awaitScreen(RegisterScreen.class);
    }
    private void fillRegistration(String email, String name, String uscId, String password) {
        fill(R.id.identity_name, name); fill(R.id.identity_email, email);
        fill(R.id.identity_usc_id, uscId); fill(R.id.identity_password, password);
    }
    private void register(String email, String name, String uscId) throws Exception {
        registerScreen();
        fillRegistration(email, name, uscId, "screen-password");
        tap(R.id.identity_submit);
        awaitScreen(GroupListScreen.class);
    }
    private void createGroup(String name) throws Exception {
        tap(R.id.groups_create);
        awaitScreen(CreateGroupScreen.class);
        fill(R.id.groups_name, name);
        fill(R.id.groups_description, "Espresso fixture");
        tap(R.id.groups_create);
        awaitScreen(GroupListScreen.class);
        awaitText(name);
    }
    private void openGroup(String name) throws Exception {
        onView(withText(name)).perform(click());
        awaitScreen(GroupDetailScreen.class);
        awaitText(name);
    }
    private void backToGroups() throws Exception {
        tap(R.id.feature_back);
        awaitScreen(GroupListScreen.class);
    }
    private void logout() throws Exception {
        instrumentation.runOnMainSync(() -> application().getContainer().getFirebaseClients().getAuth().signOut());
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), LoginScreen.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitScreen(LoginScreen.class);
    }
    private void logoutFromUi() throws Exception {
        tap(R.id.identity_logout);
        awaitScreen(LoginScreen.class);
    }
    private void login(String email) throws Exception {
        awaitScreen(LoginScreen.class);
        fill(R.id.identity_email, email);
        fill(R.id.identity_password, "screen-password");
        tap(R.id.identity_submit);
        awaitScreen(GroupListScreen.class);
    }
    private void fill(int id, String value) {
        onView(withId(id)).perform(scrollTo(), clearText(), replaceText(value), closeSoftKeyboard());
    }
    private void tap(int id) { onView(withId(id)).perform(scrollTo(), click()); }
    private String email(String label) { return "espresso-" + label + "-" + UUID.randomUUID().toString().replace("-", "") + "@usc.edu"; }
    private String id() { return String.format(Locale.ROOT, "%010d", ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L)); }

    private void awaitScreen(Class<? extends Activity> expected) throws Exception {
        await("screen " + expected.getSimpleName(), () -> {
            for (Activity current : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (expected.isInstance(current) && current.findViewById(R.id.feature_content) != null
                    && current.findViewById(R.id.feature_content).getVisibility() == View.VISIBLE) {
                    activity = current;
                    if (current.findViewById(R.id.feature_progress) == null
                        || current.findViewById(R.id.feature_progress).getVisibility() != View.VISIBLE) return true;
                }
            }
            return false;
        });
    }
    private void assertScreen(Class<? extends Activity> expected) throws Exception { awaitScreen(expected); }
    private void awaitText(String text) throws Exception {
        await("visible text: " + text, () -> containsText(activity == null ? null : activity.findViewById(android.R.id.content), text));
    }
    private void awaitNoText(String text) throws Exception {
        await("text to disappear: " + text, () -> !containsText(activity == null ? null : activity.findViewById(android.R.id.content), text));
    }
    private boolean containsText(View view, String value) {
        if (view == null) return false;
        if (view instanceof TextView label && label.isShown() && label.getText().toString().contains(value)) return true;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) if (containsText(group.getChildAt(i), value)) return true;
        return false;
    }
    private void await(String description, BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean[] result = {false};
            instrumentation.runOnMainSync(() -> result[0] = ready.getAsBoolean());
            if (result[0]) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for " + description);
    }
}
