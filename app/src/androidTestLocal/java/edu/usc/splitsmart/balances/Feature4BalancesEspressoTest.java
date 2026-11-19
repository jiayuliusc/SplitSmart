package edu.usc.splitsmart.balances;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.balances.BalancesScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Black-box Feature 4 checks against real screens and the local Firebase backend.
 * Public services arrange independent fixtures; expected amounts come from the demo checklist,
 * never from BalanceCalculator or a view model. Espresso performs UI actions and assertions.
 */
@RunWith(AndroidJUnit4.class)
public final class Feature4BalancesEspressoTest {
    private static final String PASSWORD = "feature4-password";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final FeatureServices services = ((SplitSmartApplication) instrumentation.getTargetContext()
            .getApplicationContext()).getContainer();
    private final List<User> users = new ArrayList<>();
    private final List<String> emails = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();

    private static final class Result<T> implements Callback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        T value; AppError error;
        public void onSuccess(T value) { this.value = value; done.countDown(); }
        public void onError(AppError error) { this.error = error; done.countDown(); }
    }
    private <T> T ok(Function<Callback<T>, AsyncOperation> operation) throws Exception {
        Result<T> result = new Result<>();
        instrumentation.runOnMainSync(() -> operation.apply(result));
        assertTrue("Backend callback timed out", result.done.await(30, TimeUnit.SECONDS));
        assertNull(String.valueOf(result.error), result.error);
        return result.value;
    }
    @Before public void arrangeIndependentAccounts() throws Exception {
        ok(services.auth()::signOut);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long base = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 8_000_000_000L);
        for (String name : List.of("Alice", "Bob", "Carol", "David")) {
            String email = name.toLowerCase(Locale.ROOT) + suffix + "@usc.edu";
            emails.add(email);
            String id = String.format(Locale.ROOT, "%010d", base + users.size());
            MutationResult<User> registered = ok(cb -> services.auth().register(email, PASSWORD,
                    new ProfileDraft(name, id), UUID.randomUUID().toString(), cb));
            users.add(registered.value());
            ok(services.auth()::signOut);
        }
        signIn(0);
    }
    @After public void closeScreensAndSignOut() throws Exception {
        finishScreens();
        try {
            // All fixture groups are Alice-owned. Delete through the public API, never reset shared emulator data.
            if (!users.isEmpty()) {
                ok(services.auth()::signOut); signIn(0);
                for (String group : groups) {
                    long revision = ledger(group).revision();
                    this.<MutationResult<GroupDeletion>>ok(cb -> services.groups().deleteGroup(
                            group, revision, UUID.randomUUID().toString(), cb));
                }
            }
        } finally { ok(services.auth()::signOut); finishScreens(); }
    }
    private void finishScreens() {
        instrumentation.runOnMainSync(() -> {
            for (Activity a : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED))) a.finish();
        });
        instrumentation.waitForIdleSync();
    }
    private void signIn(int index) throws Exception {
        this.<Session>ok(cb -> services.auth().signIn(emails.get(index), PASSWORD, cb));
    }
    private String group(String title) throws Exception {
        MutationResult<Group> result = ok(cb -> services.groups().createGroup(new GroupDraft(title, null),
                UUID.randomUUID().toString(), cb));
        String id = result.value().groupId(); groups.add(id);
        long revision = result.groupRevision();
        for (int i = 1; i < users.size(); i++) {
            String email = emails.get(i); long expected = revision;
            MutationResult<Group> added = ok(cb -> services.groups().addMember(id, expected, email,
                    UUID.randomUUID().toString(), cb));
            revision = added.groupRevision();
        }
        return id;
    }
    private LedgerSnapshot ledger(String group) throws Exception {
        ReadResult<LedgerSnapshot> result = ok(cb -> services.expenses().getLedger(group, ReadPolicy.FRESH, cb));
        return result.value();
    }
    private Expense expense(String group, String title, long cents, int payer, int... participants) throws Exception {
        List<String> ids = new ArrayList<>();
        for (int member : participants) ids.add(users.get(member).uid());
        ExpenseDraft draft = new ExpenseDraft(title, cents, users.get(payer).uid(), ids,
                SplitMethod.EQUAL, Category.FOOD, null, List.of());
        long revision = ledger(group).revision();
        MutationResult<Expense> result = ok(cb -> services.expenses().createExpense(group, revision, draft,
                LocalDate.now(), UUID.randomUUID().toString(), cb));
        return result.value();
    }
    private void launch(String group) {
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), BalancesScreen.class)
                .putExtra(NavigationContract.GROUP_ID, group)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    // Firebase and the calculation worker are external to Espresso's main-loop synchronization.
    // Poll only for observable screen state, then use Espresso to assert both totals and row contents.
    private void await(String label, BooleanSupplier ready) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            boolean[] value = {false};
            instrumentation.runOnMainSync(() -> value[0] = ready.getAsBoolean());
            if (value[0]) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for " + label);
    }
    private Activity balances() {
        for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
            if (a instanceof BalancesScreen) return a;
        return null;
    }
    private void totals(String pay, String receive) throws Exception {
        await("balances " + pay + " / " + receive, () -> {
            Activity a = balances();
            return a != null && a.findViewById(R.id.balances_data).isShown()
                    && pay.contentEquals(((TextView) a.findViewById(R.id.balances_owe_amount)).getText())
                    && receive.contentEquals(((TextView) a.findViewById(R.id.balances_receive_amount)).getText());
        });
        onView(withId(R.id.balances_owe_amount)).check(matches(withText(pay)));
        onView(withId(R.id.balances_receive_amount)).check(matches(withText(receive)));
    }
    private void pair(String name, String direction, String amount) {
        var row = allOf(withParent(withId(R.id.balances_rows)), hasDescendant(withText(name)));
        onView(allOf(withId(R.id.balance_direction), isDescendantOfA(row))).check(matches(withText(direction)));
        onView(allOf(withId(R.id.balance_member_amount), isDescendantOfA(row))).check(matches(withText(amount)));
    }
    private void clickControl(int id) throws Exception {
        await("enabled control " + id, () -> {
            for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                View v = a.findViewById(id);
                if (v != null && v.isShown() && v.isEnabled()) return true;
            }
            return false;
        });
        onView(withId(id)).perform(scrollTo(), click());
    }
    private void amount(String value) throws Exception {
        await("settlement form ready", () -> {
            for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                View submit = a.findViewById(R.id.settlement_submit);
                if (submit != null && submit.isShown() && submit.isEnabled()) return true;
            }
            return false;
        });
        onView(withId(R.id.settlement_amount)).perform(scrollTo(), replaceText(value), closeSoftKeyboard());
        clickControl(R.id.settlement_submit);
    }
    private void confirmDeletion() {
        onView(withId(android.R.id.button1)).perform(click());
    }

    @Test public void BB01_individualBalancesExcludeSelf() throws Exception {
        String g = group("Individual balances");
        expense(g, "Dinner", 3000, 0, 0, 1, 2);
        launch(g); totals("$0.00", "$20.00");
        pair("Bob", "Owes you", "$10.00"); pair("Carol", "Owes you", "$10.00");
        onView(allOf(withId(R.id.balance_member_name), withText("Alice"))).check(doesNotExist());
    }
    @Test public void BB02_multipleAndOppositeDebtsNetWithinPair() throws Exception {
        String g = group("Offset debts");
        expense(g, "First", 2000, 1, 0, 1); expense(g, "Second", 1000, 1, 0, 1);
        expense(g, "Opposite", 800, 0, 0, 1);
        launch(g); totals("$11.00", "$0.00"); pair("Bob", "You owe them", "$11.00");
    }
    @Test public void BB03_payableAndReceivableRemainSeparate() throws Exception {
        String g = group("Separate totals");
        expense(g, "Bob share", 2000, 0, 0, 1); expense(g, "Carol share", 1000, 0, 0, 2);
        expense(g, "David paid", 800, 3, 0, 3);
        launch(g); totals("$4.00", "$15.00");
        pair("Bob", "Owes you", "$10.00"); pair("Carol", "Owes you", "$5.00");
        pair("David", "You owe them", "$4.00");
    }
    @Test public void BB04_otherGroupDoesNotChangeDisplayedBalances() throws Exception {
        String first = group("Target group"); expense(first, "Target dinner", 2000, 1, 0, 1);
        String other = group("Unrelated group"); expense(other, "Unrelated dinner", 20000, 0, 0, 1);
        launch(other); totals("$0.00", "$100.00");
        launch(first); totals("$10.00", "$0.00"); pair("Bob", "You owe them", "$10.00");
        onView(withId(R.id.balances_group_name)).check(matches(withText("Target group")));
    }
    @Test public void BB05_noTransitiveDebtAndSwitchingViewerReversesPairs() throws Exception {
        String g = group("Pair boundaries");
        expense(g, "Alice owes Bob", 2000, 1, 0, 1); expense(g, "Bob owes Carol", 2000, 2, 1, 2);
        launch(g); totals("$10.00", "$0.00");
        pair("Bob", "You owe them", "$10.00"); pair("Carol", "Settled up", "$0.00");
        finishScreens(); ok(services.auth()::signOut); signIn(1); launch(g);
        totals("$10.00", "$10.00"); pair("Alice", "Owes you", "$10.00"); pair("Carol", "You owe them", "$10.00");
    }
    @Test public void BB06_expenseCreateEditDeleteUpdatesWithoutRefresh() throws Exception {
        String g = group("Live expenses"); launch(g); totals("$0.00", "$0.00");
        Expense e = expense(g, "Live expense", 2000, 1, 0, 1); totals("$10.00", "$0.00");
        ExpenseDraft draft = new ExpenseDraft(e.title(), 3000, e.payerId(), e.participantIds(),
                SplitMethod.EQUAL, Category.FOOD, null, List.of());
        long revision = ledger(g).revision();
        this.<MutationResult<Expense>>ok(cb -> services.expenses().updateExpense(g, revision, e.expenseId(), draft,
                UUID.randomUUID().toString(), cb));
        totals("$15.00", "$0.00"); pair("Bob", "You owe them", "$15.00");
        long deleteRevision = ledger(g).revision();
        this.<MutationResult<DeletedRecord>>ok(cb -> services.expenses().deleteExpense(g, deleteRevision,
                e.expenseId(), UUID.randomUUID().toString(), cb));
        totals("$0.00", "$0.00"); onView(withId(R.id.balances_empty)).check(matches(isDisplayed()));
    }
    @Test public void BB07_settlementUiCreateEditDeleteUpdatesWithoutRefresh() throws Exception {
        String g = group("Settlement lifecycle"); expense(g, "Debt", 2200, 1, 0, 1);
        launch(g); totals("$11.00", "$0.00");
        clickControl(R.id.balances_add_settlement);
        clickControl(R.id.settlement_payee_spinner);
        onData(is("Bob")).perform(click()); amount("4.00"); totals("$7.00", "$0.00");
        clickControl(R.id.settlement_row_edit); amount("6.00"); totals("$5.00", "$0.00");
        clickControl(R.id.settlement_row_edit); clickControl(R.id.settlement_delete);
        onView(withId(android.R.id.button2)).perform(click());
        onView(withId(R.id.settlement_amount)).check(matches(withText("6.00")));
        clickControl(R.id.settlement_delete); confirmDeletion();
        totals("$11.00", "$0.00"); pair("Bob", "You owe them", "$11.00");
    }
    @Test public void BB08_settledExpenseChangesPreserveSignedCredit() throws Exception {
        String g = group("Signed credits"); Expense e = expense(g, "Paid expense", 2000, 1, 0, 1);
        long revision = ledger(g).revision();
        this.<MutationResult<Settlement>>ok(cb -> services.settlements().createSettlement(g, revision,
                new SettlementDraft(users.get(0).uid(), users.get(1).uid(), 1000, null), LocalDate.now(),
                UUID.randomUUID().toString(), cb));
        launch(g); totals("$0.00", "$0.00");
        ExpenseDraft edited = new ExpenseDraft(e.title(), 1000, e.payerId(), e.participantIds(),
                SplitMethod.EQUAL, Category.FOOD, null, List.of());
        long editRevision = ledger(g).revision();
        this.<MutationResult<Expense>>ok(cb -> services.expenses().updateExpense(g, editRevision,
                e.expenseId(), edited, UUID.randomUUID().toString(), cb));
        totals("$0.00", "$5.00"); pair("Bob", "Owes you", "$5.00");
        long deleteRevision = ledger(g).revision();
        this.<MutationResult<DeletedRecord>>ok(cb -> services.expenses().deleteExpense(g, deleteRevision,
                e.expenseId(), UUID.randomUUID().toString(), cb));
        totals("$0.00", "$10.00"); pair("Bob", "Owes you", "$10.00");
    }
}
