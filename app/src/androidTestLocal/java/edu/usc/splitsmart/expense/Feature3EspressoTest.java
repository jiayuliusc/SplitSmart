package edu.usc.splitsmart.expenses;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.FeatureServices;
import edu.usc.splitsmart.config.SplitSmartApplication;
import edu.usc.splitsmart.domain.contract.AsyncOperation;
import edu.usc.splitsmart.domain.contract.Callback;
import edu.usc.splitsmart.domain.contract.MutationResult;
import edu.usc.splitsmart.domain.contract.ReadPolicy;
import edu.usc.splitsmart.domain.contract.ReadResult;
import edu.usc.splitsmart.domain.model.Expense;
import edu.usc.splitsmart.domain.model.Group;
import edu.usc.splitsmart.domain.model.GroupDraft;
import edu.usc.splitsmart.domain.model.LedgerSnapshot;
import edu.usc.splitsmart.domain.model.ProfileDraft;
import edu.usc.splitsmart.domain.model.Session;
import edu.usc.splitsmart.domain.model.User;
import edu.usc.splitsmart.presentation.expenses.CreateExpenseScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

// Feature 3 black-box tests. All form actions and assertions use Espresso.
@RunWith(AndroidJUnit4.class)
public final class Feature3EspressoTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private FeatureServices services;

    private static final class Result<T> implements Callback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        T value;
        Throwable error;

        @Override public void onSuccess(T value) { this.value = value; done.countDown(); }
        @Override public void onError(edu.usc.splitsmart.domain.contract.AppError error) {
            this.error = new AssertionError(error.message()); done.countDown();
        }
    }

    private static final class Fixture {
        String groupId;
        String bobEmail;
        User bob;
        User carol;
        User david;
    }

    @Before public void setUp() {
        SplitSmartApplication app = (SplitSmartApplication)
            instrumentation.getTargetContext().getApplicationContext();
        services = app.getContainer();
    }

    private <T> T ok(Function<Callback<T>, AsyncOperation> call) throws Exception {
        Result<T> result = new Result<>();
        instrumentation.runOnMainSync(() -> call.apply(result));
        assertTrue("Callback timeout", result.done.await(30, TimeUnit.SECONDS));
        assertNull(result.error);
        return result.value;
    }

    private Fixture fixture() throws Exception {
        Fixture f = new Fixture();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        f.bobEmail = "split-bob-" + suffix + "@usc.edu";
        String carolEmail = "split-carol-" + suffix + "@usc.edu";
        String davidEmail = "split-david-" + suffix + "@usc.edu";
        long base = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 7_000_000_000L);

        ok(services.auth()::signOut);
        f.bob = this.<MutationResult<User>>ok(cb -> services.auth().register(f.bobEmail, "demo-password",
            new ProfileDraft("Bob", String.format(Locale.ROOT, "%010d", base)), UUID.randomUUID().toString(), cb)).value();
        ok(services.auth()::signOut);
        f.carol = this.<MutationResult<User>>ok(cb -> services.auth().register(carolEmail, "demo-password",
            new ProfileDraft("Carol", String.format(Locale.ROOT, "%010d", base + 1)), UUID.randomUUID().toString(), cb)).value();
        ok(services.auth()::signOut);
        f.david = this.<MutationResult<User>>ok(cb -> services.auth().register(davidEmail, "demo-password",
            new ProfileDraft("David", String.format(Locale.ROOT, "%010d", base + 2)), UUID.randomUUID().toString(), cb)).value();
        ok(services.auth()::signOut);
        this.<Session>ok(cb -> services.auth().signIn(f.bobEmail, "demo-password", cb));

        MutationResult<Group> created = ok(cb -> services.groups().createGroup(
            new GroupDraft("Feature 3 " + suffix, "Expense splitting tests"), UUID.randomUUID().toString(), cb));
        f.groupId = created.value().groupId();
        MutationResult<Group> withCarol = ok(cb -> services.groups().addMember(
            f.groupId, created.value().revision(), carolEmail, UUID.randomUUID().toString(), cb));
        this.<MutationResult<Group>>ok(cb -> services.groups().addMember(
            f.groupId, withCarol.value().revision(), davidEmail, UUID.randomUUID().toString(), cb));
        return f;
    }

    private void open(Fixture fixture) throws Exception {
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CreateExpenseScreen.class)
            .putExtra(NavigationContract.GROUP_ID, fixture.groupId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await("expense form", () -> {
            for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED))) {
                if (activity instanceof CreateExpenseScreen
                        && activity.findViewById(R.id.expense_split_method_spinner) != null
                        && activity.findViewById(R.id.expense_split_method_spinner).isShown()) return true;
            }
            return false;
        });
        await("group members", () -> {
            for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED))) {
                android.view.ViewGroup participants = activity.findViewById(R.id.expense_participants_container);
                if (activity instanceof CreateExpenseScreen && participants != null && participants.getChildCount() == 3) return true;
            }
            return false;
        });
        onView(withId(R.id.expense_title)).perform(replaceText("Dinner"), closeSoftKeyboard());
    }

    private void await(String label, BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 600; i++) {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for " + label);
    }

    private void amount(String value) {
        onView(withId(R.id.expense_amount)).perform(replaceText(value), closeSoftKeyboard());
    }

    private void method(String label) throws Exception {
        onView(withId(R.id.expense_split_method_spinner)).perform(click());
        onData(is(label)).perform(click());
        await("custom split fields", () -> {
            for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED))) {
                android.view.ViewGroup inputs = activity.findViewById(R.id.expense_split_inputs_container);
                if (activity instanceof CreateExpenseScreen && inputs != null && inputs.getChildCount() == 3) return true;
            }
            return false;
        });
    }

    private void splitValue(String participant, String method, String value) {
        onView(withContentDescription(participant + ", " + method))
            .perform(scrollTo(), replaceText(value), closeSoftKeyboard());
    }

    private LedgerSnapshot ledger(String groupId) throws Exception {
        ReadResult<LedgerSnapshot> result = ok(cb -> services.expenses().getLedger(groupId, ReadPolicy.FRESH, cb));
        return result.value();
    }

    @Test public void equalSplitAllocatesIndivisibleCentAndEnablesSave() throws Exception {
        Fixture f = fixture(); open(f); amount("10.00");
        String extra = f.bob.uid().compareTo(f.carol.uid()) < 0 && f.bob.uid().compareTo(f.david.uid()) < 0 ? "Bob"
            : f.carol.uid().compareTo(f.david.uid()) < 0 ? "Carol" : "David";
        for (String name : List.of("Bob", "Carol", "David")) {
            String expected = name + (name.equals(extra) ? ": $3.34" : ": $3.33");
            await("equal preview for " + name, () -> isTextVisible(expected));
        }
        onView(withId(R.id.expense_submit)).check(matches(isEnabled()));
    }

    @Test public void validExactAmountsCanBeSaved() throws Exception {
        Fixture f = fixture(); open(f); amount("30.00"); method("Exact amounts");
        splitValue("Bob", "Exact amounts", "10.00");
        splitValue("Carol", "Exact amounts", "15.00");
        splitValue("David", "Exact amounts", "5.00");
        await("valid exact preview", () -> isTextVisible("Carol: $15.00"));
        onView(withId(R.id.expense_submit)).check(matches(isEnabled())).perform(scrollTo(), click());
        Expense saved = null;
        for (int i = 0; i < 100 && saved == null; i++) {
            List<Expense> expenses = ledger(f.groupId).expenses();
            if (!expenses.isEmpty()) saved = expenses.get(0);
            else Thread.sleep(100);
        }
        assertNotNull("Exact expense was not saved", saved);
        assertEquals("EXACT", saved.splitMethod().name());
        assertEquals(3000L, saved.shares().stream().mapToLong(value -> value.amountCents()).sum());
    }

    @Test public void exactAmountsThatDoNotMatchTotalCannotBeSaved() throws Exception {
        Fixture f = fixture(); open(f); amount("30.00"); method("Exact amounts");
        splitValue("Bob", "Exact amounts", "10.00");
        splitValue("Carol", "Exact amounts", "10.00");
        splitValue("David", "Exact amounts", "5.00");
        await("exact validation", () -> isTextVisibleContaining("must equal the expense total"));
        onView(withId(R.id.expense_split_error))
            .check(matches(withText(containsString("must equal the expense total"))));
        onView(withId(R.id.expense_submit)).check(matches(not(isEnabled())));
        assertTrue(ledger(f.groupId).expenses().isEmpty());
    }

    @Test public void percentageSplitRoundsAndStillTotalsExpense() throws Exception {
        Fixture f = fixture(); open(f); amount("10.01"); method("Percentages");
        splitValue("Bob", "Percentages", "25");
        splitValue("Carol", "Percentages", "50");
        splitValue("David", "Percentages", "25");
        await("percentage preview", () -> {
            for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED))) {
                ViewGroup rows = activity.findViewById(R.id.expense_split_preview_container);
                if (activity instanceof CreateExpenseScreen && rows != null && rows.getChildCount() == 3
                        && activity.findViewById(R.id.expense_submit).isEnabled()) return true;
            }
            return false;
        });
        List<String> rows = new ArrayList<>();
        instrumentation.runOnMainSync(() -> {
            for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED))) {
                if (!(activity instanceof CreateExpenseScreen)) continue;
                ViewGroup preview = activity.findViewById(R.id.expense_split_preview_container);
                for (int i = 0; i < preview.getChildCount(); i++)
                    rows.add(((TextView) preview.getChildAt(i)).getText().toString());
            }
        });
        long cents = 0;
        for (String row : rows) {
            int start = row.indexOf('$') + 1, end = row.indexOf('(', start);
            assertTrue(rows.toString(), start > 0 && end > start && row.contains("%"));
            cents += new BigDecimal(row.substring(start, end)).movePointRight(2).longValueExact();
        }
        assertEquals(rows.toString(), 1001, cents);
        onView(withId(R.id.expense_submit)).check(matches(isEnabled()));
    }

    @Test public void percentagesMustTotalExactlyOneHundred() throws Exception {
        Fixture f = fixture(); open(f); amount("10.00"); method("Percentages");
        splitValue("Bob", "Percentages", "33");
        splitValue("Carol", "Percentages", "33");
        splitValue("David", "Percentages", "33");
        await("percentage validation", () -> isTextVisibleContaining("100%"));
        onView(withId(R.id.expense_split_error)).check(matches(withText(containsString("100%"))));
        onView(withId(R.id.expense_submit)).check(matches(not(isEnabled())));
        assertTrue(ledger(f.groupId).expenses().isEmpty());
    }

    private boolean isTextVisible(String expected) {
        for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED))) {
            android.view.View root = activity.findViewById(android.R.id.content);
            if (root != null && containsText(root, expected, false)) return true;
        }
        return false;
    }

    private boolean isTextVisibleContaining(String expected) {
        for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED))) {
            android.view.View root = activity.findViewById(android.R.id.content);
            if (root != null && containsText(root, expected, true)) return true;
        }
        return false;
    }

    private boolean containsText(android.view.View view, String expected, boolean partial) {
        if (view instanceof android.widget.TextView) {
            String actual = ((android.widget.TextView) view).getText().toString();
            if (partial ? actual.contains(expected) : actual.equals(expected)) return true;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), expected, partial)) return true;
            }
        }
        return false;
    }
}
