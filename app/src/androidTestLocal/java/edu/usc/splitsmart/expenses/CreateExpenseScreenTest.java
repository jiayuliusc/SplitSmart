package edu.usc.splitsmart.expenses;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.SplitSmartApplication;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.expenses.CreateExpenseScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CreateExpenseScreenTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private static final class Result<T> implements Callback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        T value;
        AppError error;
        @Override public void onSuccess(T value) { this.value = value; done.countDown(); }
        @Override public void onError(AppError error) { this.error = error; done.countDown(); }
    }

    private <T> T ok(Function<Callback<T>, AsyncOperation> call) throws Exception {
        Result<T> result = new Result<>();
        instrumentation.runOnMainSync(() -> call.apply(result));
        assertTrue(result.done.await(30, TimeUnit.SECONDS));
        assertNull(String.valueOf(result.error), result.error);
        return result.value;
    }

    private void await(BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 600; attempt++) {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            Thread.sleep(50);
        }
        fail("Expense screen did not reach expected state");
    }

    private CheckBox participant(Activity screen, String uid) {
        LinearLayout rows = screen.findViewById(R.id.expense_participants_container);
        for (int i = 0; i < rows.getChildCount(); i++) {
            CheckBox box = (CheckBox) rows.getChildAt(i);
            if (uid.equals(box.getTag())) return box;
        }
        throw new AssertionError("Missing participant " + uid);
    }

    @Test public void payerIsLockedOnRenderAndSelectionWhileSaveValidationRejectsMissingPayer() throws Exception {
        SplitSmartApplication app = (SplitSmartApplication) instrumentation.getTargetContext().getApplicationContext();
        var auth = app.getContainer().auth();
        var groups = app.getContainer().groups();
        var expenses = app.getContainer().expenses();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String aliceEmail = "expense-alice-" + suffix + "@usc.edu";
        String bobEmail = "expense-bob-" + suffix + "@usc.edu";
        long numeric = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 9_000_000_000L);
        String aliceId = String.format(Locale.ROOT, "%010d", numeric);
        String bobId = String.format(Locale.ROOT, "%010d", numeric + 1);
        Activity[] screen = {null};
        try {
            ok(auth::signOut);
            MutationResult<User> alice = ok(cb -> auth.register(aliceEmail, "expense-password",
                new ProfileDraft("Alice", aliceId), UUID.randomUUID().toString(), cb));
            MutationResult<Group> group = ok(cb -> groups.createGroup(
                new GroupDraft("Payer selection test", null), UUID.randomUUID().toString(), cb));
            ok(auth::signOut);
            MutationResult<User> bob = ok(cb -> auth.register(bobEmail, "expense-password",
                new ProfileDraft("Bob", bobId), UUID.randomUUID().toString(), cb));
            ok(auth::signOut);
            this.<Session>ok(cb -> auth.signIn(aliceEmail, "expense-password", cb));
            MutationResult<Group> added = ok(cb -> groups.addMember(group.value().groupId(), group.groupRevision(), bobEmail,
                UUID.randomUUID().toString(), cb));

            instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CreateExpenseScreen.class)
                .putExtra(NavigationContract.GROUP_ID, group.value().groupId())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(() -> {
                for (Activity current : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (current.getClass() == CreateExpenseScreen.class
                        && current.findViewById(R.id.feature_content).isShown()
                        && ((Spinner) current.findViewById(R.id.expense_payer_spinner)).getCount() == 2) {
                        screen[0] = current;
                        return true;
                    }
                }
                return false;
            });
            instrumentation.runOnMainSync(() -> {
                Spinner payer = screen[0].findViewById(R.id.expense_payer_spinner);
                assertEquals(0, payer.getSelectedItemPosition());
                String initialUid = payer.getSelectedItem().equals("Alice") ? alice.value().uid() : bob.value().uid();
                String nextUid = initialUid.equals(alice.value().uid()) ? bob.value().uid() : alice.value().uid();
                CheckBox initialBox = participant(screen[0], initialUid);
                assertTrue(initialBox.isChecked());
                assertFalse(initialBox.isEnabled());
                CheckBox nextBox = participant(screen[0], nextUid);
                assertTrue(nextBox.isEnabled());
                nextBox.setChecked(false);
                assertFalse(nextBox.isChecked());
                payer.setSelection(1);
            });
            await(() -> {
                Spinner payer = screen[0].findViewById(R.id.expense_payer_spinner);
                String currentUid = payer.getSelectedItem().equals("Alice") ? alice.value().uid() : bob.value().uid();
                String previousUid = currentUid.equals(alice.value().uid()) ? bob.value().uid() : alice.value().uid();
                return payer.getSelectedItemPosition() == 1
                    && participant(screen[0], currentUid).isChecked()
                    && !participant(screen[0], currentUid).isEnabled()
                    && participant(screen[0], previousUid).isEnabled();
            });

            ExpenseDraft malformed = new ExpenseDraft("Dinner", 1000, bob.value().uid(),
                List.of(alice.value().uid()), SplitMethod.EQUAL, Category.FOOD, null, List.of());
            Result<MutationResult<Expense>> rejected = new Result<>();
            instrumentation.runOnMainSync(() -> expenses.createExpense(group.value().groupId(),
                added.groupRevision(), malformed, LocalDate.now(), UUID.randomUUID().toString(), rejected));
            assertTrue(rejected.done.await(30, TimeUnit.SECONDS));
            assertNull(rejected.value);
            assertEquals(ErrorCode.VALIDATION, rejected.error.code());
            assertEquals("participantIds", rejected.error.field());
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (screen[0] != null) screen[0].finish();
                app.getContainer().getFirebaseClients().getAuth().signOut();
            });
        }
    }
}
