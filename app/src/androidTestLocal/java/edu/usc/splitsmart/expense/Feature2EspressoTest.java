package edu.usc.splitsmart.expense;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.FeatureServices;
import edu.usc.splitsmart.config.SplitSmartApplication;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.expenses.CreateExpenseScreen;
import edu.usc.splitsmart.presentation.expenses.ExpenseDetailScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

/**
 * Feature 2 Expense Management Black-Box UI Tests B6 - B11.
 */
@RunWith(AndroidJUnit4.class)
public final class Feature2EspressoTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private FeatureServices services;
    private Activity screen;

    private static final class TestCallbackResult<T> implements Callback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        T value;
        AppError error;

        @Override
        public void onSuccess(T v) {
            this.value = v;
            done.countDown();
        }

        @Override
        public void onError(AppError e) {
            this.error = e;
            done.countDown();
        }
    }

    private <T> T ok(Function<Callback<T>, AsyncOperation> call) throws Exception {
        TestCallbackResult<T> result = new TestCallbackResult<>();
        instrumentation.runOnMainSync(() -> call.apply(result));
        assertTrue("Callback timeout", result.done.await(30, TimeUnit.SECONDS));
        assertNull("Expected success but got error: " + (result.error != null ? result.error.message() : ""), result.error);
        return result.value;
    }

    private boolean mainCheck(BooleanSupplier check) {
        boolean[] done = {false};
        instrumentation.runOnMainSync(() -> done[0] = check.getAsBoolean());
        return done[0];
    }

    private void awaitCondition(String label, BooleanSupplier check) throws Exception {
        for (int i = 0; i < 600; i++) {
            if (check.getAsBoolean()) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for: " + label);
    }

    private void awaitUi(String label, BooleanSupplier check) throws Exception {
        awaitCondition(label, () -> mainCheck(check));
    }

    private void activity(Class<?> type) throws Exception {
        awaitUi(type.getSimpleName(), () -> {
            for (Activity a : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))) {
                View content = a.findViewById(R.id.feature_content);
                View progress = a.findViewById(R.id.feature_progress);
                if (a.getClass() == type && content != null && content.isShown() && (progress == null || progress.getVisibility() != View.VISIBLE)) {
                    screen = a;
                    return true;
                }
            }
            return false;
        });
    }

    private void click(View view) {
        view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        assertTrue(view.performClick());
    }

    private void click(int id) throws Exception {
        awaitUi("enabled control " + id, () -> screen.findViewById(id) != null && screen.findViewById(id).isEnabled());
        instrumentation.runOnMainSync(() -> click(screen.findViewById(id)));
    }

    private void input(int id, String value) {
        instrumentation.runOnMainSync(() -> ((TextView) screen.findViewById(id)).setText(value));
    }

    private void selectSpinner(int id, String name) throws Exception {
        awaitUi("spinner populated", () -> {
            Spinner spinner = screen.findViewById(id);
            if (spinner == null || spinner.getAdapter() == null) return false;
            SpinnerAdapter adapter = spinner.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equals(name)) return true;
            }
            return false;
        });
        instrumentation.runOnMainSync(() -> {
            Spinner spinner = screen.findViewById(id);
            SpinnerAdapter adapter = spinner.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equals(name)) {
                    spinner.setSelection(i);
                    return;
                }
            }
            fail("Missing item in spinner: " + name);
        });
        instrumentation.waitForIdleSync();
    }

    private void positiveDialog() throws Exception {
        var device = instrumentation.getUiAutomation();
        awaitCondition("delete confirmation dialog", () -> mainCheck(() -> {
            var root = device.getRootInActiveWindow();
            if (root == null) return false;
            var buttons = root.findAccessibilityNodeInfosByViewId("android:id/button1");
            if (buttons.isEmpty()) return false;
            return buttons.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        }));
    }

    private void assertShares(ViewGroup container, Map<String, String> expectedShares) {
        assertEquals(expectedShares.size(), container.getChildCount());
        Map<String, String> actual = new HashMap<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            String text = ((TextView) container.getChildAt(i)).getText().toString();
            String[] parts = text.split(":");
            if (parts.length == 2) {
                actual.put(parts[0].trim(), parts[1].trim());
            }
        }
        for (Map.Entry<String, String> entry : expectedShares.entrySet()) {
            assertEquals("Share amount for " + entry.getKey(), entry.getValue(), actual.get(entry.getKey()));
        }
    }

    private static final class TestFixture {
        String suffix;
        String groupId;
        User bob;
        User carol;
        User david;
        String bobEmail;
        String carolEmail;
        String davidEmail;
    }

    private TestFixture setupFixture() throws Exception {
        TestFixture f = new TestFixture();
        f.suffix = UUID.randomUUID().toString().replace("-", "");
        f.bobEmail = "bob-" + f.suffix + "@usc.edu";
        f.carolEmail = "carol-" + f.suffix + "@usc.edu";
        f.davidEmail = "david-" + f.suffix + "@usc.edu";

        long numeric = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 8_000_000_000L);
        String bobId = String.format(Locale.ROOT, "%010d", numeric);
        String carolId = String.format(Locale.ROOT, "%010d", numeric + 1);
        String davidId = String.format(Locale.ROOT, "%010d", numeric + 2);

        ok(services.auth()::signOut);
        MutationResult<User> rBob = ok(cb -> services.auth().register(f.bobEmail, "demo-password", new ProfileDraft("Bob", bobId), UUID.randomUUID().toString(), cb));
        f.bob = rBob.value();
        ok(services.auth()::signOut);

        MutationResult<User> rCarol = ok(cb -> services.auth().register(f.carolEmail, "demo-password", new ProfileDraft("Carol", carolId), UUID.randomUUID().toString(), cb));
        f.carol = rCarol.value();
        ok(services.auth()::signOut);

        MutationResult<User> rDavid = ok(cb -> services.auth().register(f.davidEmail, "demo-password", new ProfileDraft("David", davidId), UUID.randomUUID().toString(), cb));
        f.david = rDavid.value();
        ok(services.auth()::signOut);

        // Sign in as Bob and create group
        this.<Session>ok(cb -> services.auth().signIn(f.bobEmail, "demo-password", cb));
        MutationResult<Group> gRes = ok(cb -> services.groups().createGroup(new GroupDraft("Test Group", "Group for Feature 2"), UUID.randomUUID().toString(), cb));
        f.groupId = gRes.value().groupId();

        // Add Carol and David to group
        long rev1 = gRes.value().revision();
        MutationResult<Group> gRes2 = this.<MutationResult<Group>>ok(cb -> services.groups().addMember(f.groupId, rev1, f.carolEmail, UUID.randomUUID().toString(), cb));
        long rev2 = gRes2.value().revision();
        this.<MutationResult<Group>>ok(cb -> services.groups().addMember(f.groupId, rev2, f.davidEmail, UUID.randomUUID().toString(), cb));

        return f;
    }

    private void loginAs(String email) throws Exception {
        instrumentation.runOnMainSync(() -> {
            for (Activity a : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))) {
                a.finish();
            }
        });
        ok(services.auth()::signOut);
        this.<Session>ok(cb -> services.auth().signIn(email, "demo-password", cb));
    }

    private LedgerSnapshot fetchLedger(String groupId) throws Exception {
        ReadResult<LedgerSnapshot> read = ok(cb -> services.expenses().getLedger(groupId, ReadPolicy.FRESH, cb));
        return read.value();
    }

    @Before
    public void setup() {
        SplitSmartApplication app = (SplitSmartApplication) instrumentation.getTargetContext().getApplicationContext();
        services = app.getContainer();
    }

    /**
     * B6 - Create an expense with all fields
     * Method: createExpenseWithAllFieldsTest()
     */
    @Test
    public void createExpenseWithAllFieldsTest() throws Exception {
        TestFixture f = setupFixture();
        loginAs(f.bobEmail);

        // Bob seeds an expense with title Movie, amount $45.00 (4500 cents), payer Carol, category Transportation,
        // participants Bob & Carol, percentage split Bob 40% ($18.00) & Carol 60% ($27.00), note "tickets for Godzilla".
        long rev = fetchLedger(f.groupId).revision();
        ExpenseDraft draft = new ExpenseDraft(
            "Movie", 4500, f.carol.uid(), List.of(f.bob.uid(), f.carol.uid()),
            SplitMethod.PERCENTAGE, Category.TRANSPORTATION, "tickets for Godzilla",
            List.of(
                new SplitInput(f.bob.uid(), null, 4000),
                new SplitInput(f.carol.uid(), null, 6000)
            )
        );
        MutationResult<Expense> created = ok(cb -> services.expenses().createExpense(f.groupId, rev, draft, LocalDate.now(), UUID.randomUUID().toString(), cb));
        String expenseId = created.value().expenseId();

        // Launch ExpenseDetailScreen as Bob and verify persistence
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ExpenseDetailScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(ExpenseDetailScreen.class);

        instrumentation.runOnMainSync(() -> {
            assertEquals("Movie", ((TextView) screen.findViewById(R.id.feature_title)).getText().toString());
            assertEquals("$45.00", ((TextView) screen.findViewById(R.id.expense_detail_amount)).getText().toString());
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_category)).getText().toString().contains("TRANSPORTATION"));
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_payer)).getText().toString().contains("Carol"));
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_date)).getText().toString().contains(LocalDate.now().toString()));
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_note)).getText().toString().contains("tickets for Godzilla"));

            // Check shares: Bob $18.00 and Carol $27.00 (order-independent)
            ViewGroup sharesContainer = screen.findViewById(R.id.expense_shares_container);
            assertShares(sharesContainer, Map.of("Bob", "$18.00", "Carol", "$27.00"));
        });

        // Log in as David and open the expense
        loginAs(f.davidEmail);
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ExpenseDetailScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(ExpenseDetailScreen.class);

        instrumentation.runOnMainSync(() -> {
            assertEquals("Movie", ((TextView) screen.findViewById(R.id.feature_title)).getText().toString());
            assertEquals("$45.00", ((TextView) screen.findViewById(R.id.expense_detail_amount)).getText().toString());
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_category)).getText().toString().contains("TRANSPORTATION"));
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_payer)).getText().toString().contains("Carol"));
            // David can view the expense, non-creator buttons hidden
            assertEquals(View.GONE, screen.findViewById(R.id.expense_edit).getVisibility());
            assertEquals(View.GONE, screen.findViewById(R.id.expense_delete).getVisibility());
        });
    }

    /**
     * B7 - Default values and available categories
     * Method: defaultExpenseFieldsTest()
     */
    @Test
    public void defaultExpenseFieldsTest() throws Exception {
        TestFixture f = setupFixture();
        loginAs(f.bobEmail);

        // Open a new expense screen
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CreateExpenseScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(CreateExpenseScreen.class);

        // Inspect category choices
        instrumentation.runOnMainSync(() -> {
            Spinner catSpinner = screen.findViewById(R.id.expense_category_spinner);
            assertNotNull(catSpinner);
            SpinnerAdapter adapter = catSpinner.getAdapter();
            Set<String> categories = new HashSet<>();
            for (int i = 0; i < adapter.getCount(); i++) {
                categories.add(adapter.getItem(i).toString());
            }
            assertTrue(categories.containsAll(List.of("FOOD", "HOUSING", "TRANSPORTATION", "ENTERTAINMENT", "OTHER")));
        });

        // Enter title "Default", amount "$20.00", select participants Bob and David (uncheck Carol)
        input(R.id.expense_title, "Default");
        input(R.id.expense_amount, "20.00");

        instrumentation.runOnMainSync(() -> {
            ViewGroup container = screen.findViewById(R.id.expense_participants_container);
            for (int i = 0; i < container.getChildCount(); i++) {
                CheckBox box = (CheckBox) container.getChildAt(i);
                if (box.getText().toString().equals("Carol")) {
                    box.setChecked(false);
                } else if (box.getText().toString().equals("Bob") || box.getText().toString().equals("David")) {
                    box.setChecked(true);
                }
            }
        });

        // The form has no signed-in-user payer default; select Bob explicitly.
        selectSpinner(R.id.expense_payer_spinner, "Bob");
        // Leave the remaining optional and default fields unchanged.
        click(R.id.expense_submit);

        // Wait for created expense in ledger
        awaitCondition("expense created", () -> {
            try {
                return !fetchLedger(f.groupId).expenses().isEmpty();
            } catch (Exception e) {
                return false;
            }
        });

        Expense created = fetchLedger(f.groupId).expenses().get(0);
        assertEquals("Default", created.title());
        assertEquals(2000, created.amountCents());
        assertEquals(Category.OTHER, created.category());
        assertEquals(f.bob.uid(), created.payerId());
        assertEquals(SplitMethod.EQUAL, created.splitMethod());
        assertNull(created.note());

        // Reopen expense in detail screen and verify
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ExpenseDetailScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, created.expenseId())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(ExpenseDetailScreen.class);

        instrumentation.runOnMainSync(() -> {
            assertEquals("Default", ((TextView) screen.findViewById(R.id.feature_title)).getText().toString());
            assertEquals("$20.00", ((TextView) screen.findViewById(R.id.expense_detail_amount)).getText().toString());
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_category)).getText().toString().contains("OTHER"));
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_payer)).getText().toString().contains("Bob"));

            ViewGroup sharesContainer = screen.findViewById(R.id.expense_shares_container);
            assertShares(sharesContainer, Map.of("Bob", "$10.00", "David", "$10.00"));
        });
    }

    /**
     * B8 - Payer and participant restrictions
     * Method: payerAndParticipantValidationTest()
     */
    @Test
    public void payerAndParticipantValidationTest() throws Exception {
        TestFixture f = setupFixture();
        loginAs(f.bobEmail);

        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CreateExpenseScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(CreateExpenseScreen.class);

        input(R.id.expense_title, "Bus Tickets");
        input(R.id.expense_amount, "20.00");
        selectSpinner(R.id.expense_payer_spinner, "Bob");

        // Verify Bob's checkbox (the payer) is automatically checked and disabled (UI prevents excluding payer)
        instrumentation.runOnMainSync(() -> {
            ViewGroup container = screen.findViewById(R.id.expense_participants_container);
            for (int i = 0; i < container.getChildCount(); i++) {
                CheckBox box = (CheckBox) container.getChildAt(i);
                if (box.getText().toString().equals("Bob")) {
                    assertTrue("Payer checkbox must be checked", box.isChecked());
                    assertFalse("Payer checkbox must be disabled to prevent excluding payer", box.isEnabled());
                } else {
                    box.setChecked(false); // Uncheck other participants to test 1 participant scenario
                }
            }
        });

        awaitUi("participant validation", () -> {
            TextView error = screen.findViewById(R.id.expense_split_error);
            return error.isShown() && error.getText().toString().contains("at least 2")
                && !screen.findViewById(R.id.expense_submit).isEnabled();
        });
        assertTrue(fetchLedger(f.groupId).expenses().isEmpty());

        // Now add Carol to participants (2 distinct participants)
        instrumentation.runOnMainSync(() -> {
            ViewGroup container = screen.findViewById(R.id.expense_participants_container);
            for (int i = 0; i < container.getChildCount(); i++) {
                CheckBox box = (CheckBox) container.getChildAt(i);
                if (box.getText().toString().equals("Carol")) {
                    box.setChecked(true);
                }
            }
        });

        // Save succeeds
        click(R.id.expense_submit);

        awaitCondition("expense created after adding participant", () -> {
            try {
                return !fetchLedger(f.groupId).expenses().isEmpty();
            } catch (Exception e) {
                return false;
            }
        });

        Expense created = fetchLedger(f.groupId).expenses().get(0);
        assertEquals("Bus Tickets", created.title());
        assertEquals(2, created.participantIds().size());
        assertTrue(created.participantIds().containsAll(List.of(f.bob.uid(), f.carol.uid())));
    }

    /**
     * B9 - Invalid expense amounts
     * Method: expenseAmountValidationTest()
     */
    @Test
    public void expenseAmountValidationTest() throws Exception {
        TestFixture f = setupFixture();
        loginAs(f.bobEmail);

        // Test invalid amounts: blank, 0, -12, 12.345
        List<String> invalidAmounts = List.of("", "0", "-12", "12.345");

        for (String invalidAmt : invalidAmounts) {
            instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CreateExpenseScreen.class)
                .putExtra(NavigationContract.GROUP_ID, f.groupId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activity(CreateExpenseScreen.class);

            input(R.id.expense_title, "Invalid Amount Test");
            input(R.id.expense_amount, invalidAmt);
            selectSpinner(R.id.expense_payer_spinner, "Bob");

            awaitUi("invalid amount", () -> {
                TextView error = screen.findViewById(R.id.expense_split_error);
                return error.isShown() && !error.getText().toString().isEmpty()
                    && !screen.findViewById(R.id.expense_submit).isEnabled();
            });

            // Verify no expense was created
            assertTrue("No expense must be created for invalid amount " + invalidAmt, fetchLedger(f.groupId).expenses().isEmpty());
        }

        // Test valid amounts: $0.01 and $12.34
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CreateExpenseScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(CreateExpenseScreen.class);

        input(R.id.expense_title, "Small Cent Test");
        input(R.id.expense_amount, "0.01");
        selectSpinner(R.id.expense_payer_spinner, "Bob");
        click(R.id.expense_submit);

        awaitCondition("0.01 expense created", () -> {
            try {
                return fetchLedger(f.groupId).expenses().size() == 1;
            } catch (Exception e) {
                return false;
            }
        });
        assertEquals(1L, fetchLedger(f.groupId).expenses().get(0).amountCents());

        // Test $12.34
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CreateExpenseScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(CreateExpenseScreen.class);

        input(R.id.expense_title, "Normal Amount Test");
        input(R.id.expense_amount, "12.34");
        selectSpinner(R.id.expense_payer_spinner, "Bob");
        click(R.id.expense_submit);

        awaitCondition("12.34 expense created", () -> {
            try {
                return fetchLedger(f.groupId).expenses().size() == 2;
            } catch (Exception e) {
                return false;
            }
        });
        Expense second = fetchLedger(f.groupId).expenses().stream().filter(e -> e.title().equals("Normal Amount Test")).findFirst().orElseThrow();
        assertEquals(1234L, second.amountCents());
    }

    /**
     * B10 - Creator-only edit and delete permissions
     * Method: nonCreatorCannotEditOrDeleteTest()
     */
    @Test
    public void nonCreatorCannotEditOrDeleteTest() throws Exception {
        TestFixture f = setupFixture();
        loginAs(f.bobEmail);

        // Seed an expense created by Bob and paid by Carol
        long rev = fetchLedger(f.groupId).revision();
        ExpenseDraft draft = new ExpenseDraft(
            "Lunch", 3000, f.carol.uid(), List.of(f.bob.uid(), f.carol.uid(), f.david.uid()),
            SplitMethod.EQUAL, Category.FOOD, "Team lunch", List.of()
        );
        MutationResult<Expense> created = ok(cb -> services.expenses().createExpense(f.groupId, rev, draft, LocalDate.now(), UUID.randomUUID().toString(), cb));
        String expenseId = created.value().expenseId();

        // Log in as Carol (payer, but NOT creator) and view expense
        loginAs(f.carolEmail);
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ExpenseDetailScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(ExpenseDetailScreen.class);

        // Edit and Delete controls must be GONE
        instrumentation.runOnMainSync(() -> {
            assertEquals("Lunch", ((TextView) screen.findViewById(R.id.feature_title)).getText().toString());
            assertEquals(View.GONE, screen.findViewById(R.id.expense_edit).getVisibility());
            assertEquals(View.GONE, screen.findViewById(R.id.expense_delete).getVisibility());
        });

        // Log in as David (member, NOT creator) and view expense
        loginAs(f.davidEmail);
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ExpenseDetailScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(ExpenseDetailScreen.class);

        instrumentation.runOnMainSync(() -> {
            assertEquals("Lunch", ((TextView) screen.findViewById(R.id.feature_title)).getText().toString());
            assertEquals(View.GONE, screen.findViewById(R.id.expense_edit).getVisibility());
            assertEquals(View.GONE, screen.findViewById(R.id.expense_delete).getVisibility());
        });

        // Reopen as Bob (creator) - Bob sees edit and delete buttons
        loginAs(f.bobEmail);
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ExpenseDetailScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(ExpenseDetailScreen.class);

        instrumentation.runOnMainSync(() -> {
            assertEquals(View.VISIBLE, screen.findViewById(R.id.expense_edit).getVisibility());
            assertEquals(View.VISIBLE, screen.findViewById(R.id.expense_delete).getVisibility());
        });
    }

    /**
     * B11 - Creator edits, persistence, and deletion
     * Method: creatorEditDeletePersistenceTest()
     */
    @Test
    public void creatorEditDeletePersistenceTest() throws Exception {
        TestFixture f = setupFixture();
        loginAs(f.bobEmail);

        // Seed Bob's Movie expense
        long rev = fetchLedger(f.groupId).revision();
        ExpenseDraft draft = new ExpenseDraft(
            "Movie", 4500, f.carol.uid(), List.of(f.bob.uid(), f.carol.uid()),
            SplitMethod.EQUAL, Category.ENTERTAINMENT, "tickets", List.of()
        );
        MutationResult<Expense> created = ok(cb -> services.expenses().createExpense(f.groupId, rev, draft, LocalDate.now(), UUID.randomUUID().toString(), cb));
        String expenseId = created.value().expenseId();
        LocalDate creationDate = created.value().localDate();

        // As Bob, edit the expense: change title to Electric Bill, amount $60.00, category Housing, payer Bob,
        // participants Bob & David, Equal split, note "November electricity"
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CreateExpenseScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(CreateExpenseScreen.class);

        input(R.id.expense_title, "Electric Bill");
        input(R.id.expense_amount, "60.00");
        selectSpinner(R.id.expense_payer_spinner, "Bob");
        selectSpinner(R.id.expense_category_spinner, "HOUSING");
        input(R.id.expense_note, "November electricity");

        instrumentation.runOnMainSync(() -> {
            ViewGroup container = screen.findViewById(R.id.expense_participants_container);
            for (int i = 0; i < container.getChildCount(); i++) {
                CheckBox box = (CheckBox) container.getChildAt(i);
                if (box.getText().toString().equals("Carol")) box.setChecked(false);
                else if (box.getText().toString().equals("Bob") || box.getText().toString().equals("David")) box.setChecked(true);
            }
        });

        click(R.id.expense_submit);

        awaitCondition("expense updated in ledger", () -> {
            try {
                List<Expense> exps = fetchLedger(f.groupId).expenses();
                if (exps.isEmpty()) return false;
                Expense updated = exps.get(0);
                return "Electric Bill".equals(updated.title()) && updated.amountCents() == 6000;
            } catch (Exception e) {
                return false;
            }
        });

        // Log in as David and open the expense to see the update
        loginAs(f.davidEmail);
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ExpenseDetailScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(ExpenseDetailScreen.class);

        instrumentation.runOnMainSync(() -> {
            assertEquals("Electric Bill", ((TextView) screen.findViewById(R.id.feature_title)).getText().toString());
            assertEquals("$60.00", ((TextView) screen.findViewById(R.id.expense_detail_amount)).getText().toString());
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_category)).getText().toString().contains("HOUSING"));
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_payer)).getText().toString().contains("Bob"));
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_date)).getText().toString().contains(creationDate.toString()));
            assertTrue(((TextView) screen.findViewById(R.id.expense_detail_note)).getText().toString().contains("November electricity"));

            ViewGroup sharesContainer = screen.findViewById(R.id.expense_shares_container);
            assertShares(sharesContainer, Map.of("Bob", "$30.00", "David", "$30.00"));
        });

        // Return as Bob and delete it
        loginAs(f.bobEmail);
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ExpenseDetailScreen.class)
            .putExtra(NavigationContract.GROUP_ID, f.groupId)
            .putExtra(NavigationContract.EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(ExpenseDetailScreen.class);

        click(R.id.expense_delete);
        positiveDialog();

        awaitCondition("expense deleted in ledger", () -> {
            try {
                return fetchLedger(f.groupId).expenses().isEmpty();
            } catch (Exception e) {
                return false;
            }
        });

        // As David, refresh and check again: expense remains absent
        loginAs(f.davidEmail);
        assertTrue("Expense must remain absent for David after deletion", fetchLedger(f.groupId).expenses().isEmpty());
    }
}
