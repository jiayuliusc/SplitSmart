package edu.usc.splitsmart.balances;

import static org.junit.Assert.*;
import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.balances.BalancesScreen;
import edu.usc.splitsmart.presentation.expenses.*;
import edu.usc.splitsmart.presentation.identity.LoginScreen;
import edu.usc.splitsmart.presentation.settlements.SettlementFormScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Executes the presentation checklist against real Android screens, Auth, Firestore and callables. */
@RunWith(AndroidJUnit4.class)
public final class FinancialDemoTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final FeatureServices services=((SplitSmartApplication)instrumentation.getTargetContext().getApplicationContext()).getContainer();
    private Activity screen;
    private final List<User> users=new ArrayList<>();
    private final List<String> emails=new ArrayList<>();
    private static final class Result<T> implements Callback<T> {
        final CountDownLatch done=new CountDownLatch(1); T value; AppError error;
        public void onSuccess(T value){this.value=value;done.countDown();}
        public void onError(AppError error){this.error=error;done.countDown();}
    }
    private <T> T ok(Function<Callback<T>,AsyncOperation> operation) throws Exception {
        Result<T> result=new Result<>();instrumentation.runOnMainSync(() -> operation.apply(result));
        assertTrue("callback deadline",result.done.await(30,TimeUnit.SECONDS));assertNull(String.valueOf(result.error),result.error);return result.value;
    }
    private void await(String label,BooleanSupplier check) throws Exception {
        for(int i=0;i<600;i++) {
            boolean[] value={false};instrumentation.runOnMainSync(() -> value[0]=check.getAsBoolean());
            if(value[0])return;Thread.sleep(50);
        }
        capture("timeout");fail("Timed out: "+label);
    }
    private void activity(Class<?> type) throws Exception {
        await(type.getSimpleName(),() -> {
            for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                View content=a.findViewById(R.id.feature_content), progress=a.findViewById(R.id.feature_progress);
                if(a.getClass()==type && content!=null && content.isShown() && (progress==null || progress.getVisibility()!=View.VISIBLE)) {screen=a;return true;}
            }return false;
        });
    }
    private void launch(String group) throws Exception {
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),BalancesScreen.class)
            .putExtra(NavigationContract.GROUP_ID,group).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity(BalancesScreen.class);
    }
    private void totals(String pay,String receive) throws Exception {
        await("totals "+pay+" / "+receive,() -> screen.getClass()==BalancesScreen.class && screen.findViewById(R.id.balances_data).isShown()
            && ((TextView)screen.findViewById(R.id.balances_owe_amount)).getText().toString().equals(pay)
            && ((TextView)screen.findViewById(R.id.balances_receive_amount)).getText().toString().equals(receive));
    }
    private void click(View view) {view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true);assertTrue(view.performClick());}
    private void click(int id) throws Exception {
        await("enabled control "+id,() -> screen.findViewById(id).isEnabled());
        instrumentation.runOnMainSync(() -> click(screen.findViewById(id)));
    }
    private void input(int id,String value){instrumentation.runOnMainSync(() -> ((TextView)screen.findViewById(id)).setText(value));}
    private void select(int id,String name) {
        instrumentation.runOnMainSync(() -> {
            Spinner spinner=screen.findViewById(id);SpinnerAdapter adapter=spinner.getAdapter();
            for(int i=0;i<adapter.getCount();i++)if(adapter.getItem(i).toString().equals(name)){spinner.setSelection(i);return;}
            fail("Missing payee/payer "+name);
        });instrumentation.waitForIdleSync();
    }
    private void positiveDialog() throws Exception {
        // UiAutomation operates the real confirmation dialog instead of invoking a mutation directly.
        var device=instrumentation.getUiAutomation();
        await("delete confirmation",() -> {
            var root=device.getRootInActiveWindow();if(root==null)return false;
            var buttons=root.findAccessibilityNodeInfosByViewId("android:id/button1");
            if(buttons.isEmpty())return false;return buttons.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        });
    }
    private LedgerSnapshot ledger(String group) throws Exception {
        ReadResult<LedgerSnapshot> read=ok(cb -> services.expenses().getLedger(group,ReadPolicy.FRESH,cb));return read.value();
    }
    private Group group(String title,int... members) throws Exception {
        MutationResult<Group> created=ok(cb -> services.groups().createGroup(new GroupDraft(title,null),UUID.randomUUID().toString(),cb));
        Group value=created.value();
        for(int member:members){long revision=value.revision();MutationResult<Group> added=ok(cb -> services.groups().addMember(created.value().groupId(),revision,emails.get(member),UUID.randomUUID().toString(),cb));value=added.value();}
        return value;
    }
    private Expense expense(String group,String title,long cents,int payer,int... participants) throws Exception {
        List<String> ids=Arrays.stream(participants).mapToObj(i -> users.get(i).uid()).collect(java.util.stream.Collectors.toList());
        ExpenseDraft draft=new ExpenseDraft(title,cents,users.get(payer).uid(),ids,SplitMethod.EQUAL,Category.FOOD,null,List.of());
        long revision=ledger(group).revision();MutationResult<Expense> result=ok(cb -> services.expenses().createExpense(group,revision,draft,LocalDate.now(),UUID.randomUUID().toString(),cb));return result.value();
    }
    private void account(int index,String group) throws Exception {
        ok(services.auth()::signOut);activity(LoginScreen.class);
        this.<Session>ok(cb -> services.auth().signIn(emails.get(index),"demo-password",cb));launch(group);
    }
    private View settlementRow(){return ((ViewGroup)screen.findViewById(R.id.balances_settlements_rows)).getChildAt(0);}
    private void editPayment() throws Exception {instrumentation.runOnMainSync(() -> click(settlementRow().findViewById(R.id.settlement_row_edit)));activity(SettlementFormScreen.class);}
    private void record(String amount) throws Exception {
        click(R.id.balances_add_settlement);activity(SettlementFormScreen.class);
        select(R.id.settlement_payee_spinner,"Bob");input(R.id.settlement_amount,amount);input(R.id.settlement_note,"Cash payment");
        capture("settlement-form");click(R.id.settlement_submit);activity(BalancesScreen.class);
    }
    private void openExpense(String title) throws Exception {
        instrumentation.runOnMainSync(() -> {
            ViewGroup rows=screen.findViewById(R.id.balances_expenses_rows);
            for(int i=0;i<rows.getChildCount();i++)if(((TextView)rows.getChildAt(i).findViewById(R.id.row_expense_title)).getText().toString().equals(title)) {click(rows.getChildAt(i));return;}
            fail("Missing expense "+title);
        });activity(ExpenseDetailScreen.class);
    }
    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync();instrumentation.getUiAutomation().waitForIdle(150,5000);
        Bitmap image=instrumentation.getUiAutomation().takeScreenshot();if(image==null)return;
        File file=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"financial-"+name+".png");
        try(FileOutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        try(InputStream copy=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation()
                .executeShellCommand("cp "+file.getAbsolutePath()+" /sdcard/Download/"+file.getName()))) {
            byte[] bytes=new byte[256];while(copy.read(bytes)!=-1){ }
        }
    }
    @Test public void expensePayerCheckboxFollowsSelectionAndLockedEdits() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","");
        long base=Math.floorMod(UUID.randomUUID().getMostSignificantBits(),8_000_000_000L);
        try {
            ok(services.auth()::signOut);
            for(String name:List.of("Alice","Bob")) {
                String email=name.toLowerCase(Locale.ROOT)+suffix+"@usc.edu";emails.add(email);
                String id=String.format(Locale.ROOT,"%010d",base+users.size());
                users.add(this.<MutationResult<User>>ok(cb -> services.auth().register(email,"demo-password",new ProfileDraft(name,id),UUID.randomUUID().toString(),cb)).value());
                ok(services.auth()::signOut);
            }
            this.<Session>ok(cb -> services.auth().signIn(emails.get(0),"demo-password",cb));
            String group=group("Payer checkbox test",1).groupId();
            instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),CreateExpenseScreen.class)
                .putExtra(NavigationContract.GROUP_ID,group).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activity(CreateExpenseScreen.class);
            await("participants",() -> ((Spinner)screen.findViewById(R.id.expense_payer_spinner)).getCount()==2);
            instrumentation.runOnMainSync(() -> {
                Spinner payer=screen.findViewById(R.id.expense_payer_spinner);
                ViewGroup boxes=screen.findViewById(R.id.expense_participants_container);
                int initial=payer.getSelectedItemPosition();
                CheckBox current=(CheckBox)boxes.getChildAt(initial),next=(CheckBox)boxes.getChildAt(1-initial);
                assertTrue(current.isChecked());assertFalse(current.isEnabled());
                next.setChecked(false);assertFalse(next.isChecked());payer.setSelection(1-initial);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                Spinner payer=screen.findViewById(R.id.expense_payer_spinner);
                ViewGroup boxes=screen.findViewById(R.id.expense_participants_container);
                int selected=payer.getSelectedItemPosition();
                CheckBox current=(CheckBox)boxes.getChildAt(selected),previous=(CheckBox)boxes.getChildAt(1-selected);
                assertTrue(current.isChecked());assertFalse(current.isEnabled());assertTrue(previous.isEnabled());
            });
            ExpenseDraft malformed=new ExpenseDraft("Dinner",1000,users.get(0).uid(),List.of(users.get(1).uid()),
                SplitMethod.EQUAL,Category.FOOD,null,List.of());
            Result<MutationResult<Expense>> rejected=new Result<>();
            long revision=ledger(group).revision();
            instrumentation.runOnMainSync(() -> services.expenses().createExpense(group,revision,malformed,
                LocalDate.now(),UUID.randomUUID().toString(),rejected));
            assertTrue(rejected.done.await(30,TimeUnit.SECONDS));assertNull(rejected.value);
            assertEquals(ErrorCode.VALIDATION,rejected.error.code());
            instrumentation.runOnMainSync(screen::finish);

            List<String> ids=new ArrayList<>(List.of(users.get(0).uid(),users.get(1).uid()));Collections.sort(ids);
            for(SplitMethod method:List.of(SplitMethod.EXACT,SplitMethod.PERCENTAGE)) {
                List<SplitInput> inputs=new ArrayList<>();
                for(String uid:ids)inputs.add(method==SplitMethod.EXACT
                    ? new SplitInput(uid,500L,null):new SplitInput(uid,null,5000));
                ExpenseDraft draft=new ExpenseDraft("Locked split",1000,users.get(0).uid(),ids,method,Category.FOOD,null,inputs);
                long currentRevision=ledger(group).revision();
                Expense saved=this.<MutationResult<Expense>>ok(cb -> services.expenses().createExpense(group,currentRevision,draft,
                    LocalDate.now(),UUID.randomUUID().toString(),cb)).value();
                instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),CreateExpenseScreen.class)
                    .putExtra(NavigationContract.GROUP_ID,group).putExtra(NavigationContract.EXPENSE_ID,saved.expenseId())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                activity(CreateExpenseScreen.class);
                await("locked edit",() -> ((Spinner)screen.findViewById(R.id.expense_split_method_spinner)).getSelectedItemPosition()==method.ordinal()
                    && ((Spinner)screen.findViewById(R.id.expense_payer_spinner)).getCount()==2);
                instrumentation.runOnMainSync(() -> {
                    ViewGroup boxes=screen.findViewById(R.id.expense_participants_container);
                    for(int i=0;i<boxes.getChildCount();i++) {
                        CheckBox box=(CheckBox)boxes.getChildAt(i);assertTrue(box.isChecked());assertFalse(box.isEnabled());
                    }
                    Spinner payer=screen.findViewById(R.id.expense_payer_spinner);payer.setSelection(1-payer.getSelectedItemPosition());
                });
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> {
                    ViewGroup boxes=screen.findViewById(R.id.expense_participants_container);
                    Spinner payer=screen.findViewById(R.id.expense_payer_spinner);
                    assertTrue(((CheckBox)boxes.getChildAt(payer.getSelectedItemPosition())).isChecked());
                    for(int i=0;i<boxes.getChildCount();i++)assertFalse(boxes.getChildAt(i).isEnabled());
                    screen.finish();
                });
            }
        } finally {
            instrumentation.runOnMainSync(() -> {
                ((SplitSmartApplication)instrumentation.getTargetContext().getApplicationContext()).getContainer().getFirebaseClients().getAuth().signOut();
                for(Activity a:new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)))a.finish();
            });
        }
    }
    @Test public void completeBalanceDemoWithoutManualRefresh() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","");
        long base=Math.floorMod(UUID.randomUUID().getMostSignificantBits(),8_000_000_000L);
        try {
            ok(services.auth()::signOut);
            for(String name:List.of("Alice","Bob","Carol","David")) {
                String email=name.toLowerCase(Locale.ROOT)+suffix+"@usc.edu";emails.add(email);
                String id=String.format(Locale.ROOT,"%010d",base+users.size());
                MutationResult<User> registered=ok(cb -> services.auth().register(email,"demo-password",new ProfileDraft(name,id),UUID.randomUUID().toString(),cb));
                users.add(registered.value());ok(services.auth()::signOut);
            }
            this.<Session>ok(cb -> services.auth().signIn(emails.get(0),"demo-password",cb));
            String pair=group("Pairwise demo",1,2,3).groupId();
            expense(pair,"First shared expense",2000,1,0,1);
            Expense second=expense(pair,"Second shared expense",1000,1,0,1);
            expense(pair,"Opposite direction",800,0,0,1);
            expense(pair,"Bob and Carol",2000,2,1,2);
            launch(pair);totals("$11.00","$0.00");capture("pairwise");
            account(1,pair);totals("$10.00","$11.00");
            account(0,pair);totals("$11.00","$0.00");
            Expense remote=expense(pair,"Live change",400,1,0,1);totals("$13.00","$0.00");
            long rev=ledger(pair).revision();
            ExpenseDraft changed=new ExpenseDraft(remote.title(),800,remote.payerId(),remote.participantIds(),SplitMethod.EQUAL,Category.FOOD,null,List.of());
            this.<MutationResult<Expense>>ok(cb -> services.expenses().updateExpense(pair,rev,remote.expenseId(),changed,UUID.randomUUID().toString(),cb));totals("$15.00","$0.00");
            long deleteRev=ledger(pair).revision();
            this.<MutationResult<DeletedRecord>>ok(cb -> services.expenses().deleteExpense(pair,deleteRev,remote.expenseId(),UUID.randomUUID().toString(),cb));totals("$11.00","$0.00");
            record("4.00");totals("$7.00","$0.00");
            instrumentation.runOnMainSync(() -> settlementRow().requestRectangleOnScreen(new Rect(0,0,settlementRow().getWidth(),settlementRow().getHeight()),true));
            capture("settlement-list");
            account(1,pair);totals("$10.00","$7.00");
            instrumentation.runOnMainSync(() -> assertEquals(View.GONE,settlementRow().findViewById(R.id.settlement_row_edit).getVisibility()));
            account(0,pair);totals("$7.00","$0.00");
            editPayment();input(R.id.settlement_amount,"6.00");click(R.id.settlement_submit);activity(BalancesScreen.class);totals("$5.00","$0.00");
            editPayment();click(R.id.settlement_delete);positiveDialog();activity(BalancesScreen.class);totals("$11.00","$0.00");
            record("11.00");totals("$0.00","$0.00");
            long settledRevision=ledger(pair).revision();
            this.<MutationResult<DeletedRecord>>ok(cb -> services.expenses().deleteExpense(pair,settledRevision,second.expenseId(),UUID.randomUUID().toString(),cb));
            totals("$0.00","$5.00");capture("preserved-credit");
            click(R.id.balances_add_expense);activity(CreateExpenseScreen.class);
            input(R.id.expense_title,"UI expense");input(R.id.expense_amount,"4.00");select(R.id.expense_payer_spinner,"Bob");
            instrumentation.runOnMainSync(() -> {
                ViewGroup participants=screen.findViewById(R.id.expense_participants_container);
                for(int i=0;i<participants.getChildCount();i++){CheckBox box=(CheckBox)participants.getChildAt(i);box.setChecked(box.getText().toString().equals("Alice") || box.getText().toString().equals("Bob"));}
            });
            click(R.id.expense_submit);activity(BalancesScreen.class);totals("$0.00","$3.00");
            openExpense("UI expense");click(R.id.expense_edit);activity(CreateExpenseScreen.class);input(R.id.expense_amount,"6.00");
            click(R.id.expense_submit);activity(ExpenseDetailScreen.class);click(R.id.feature_back);activity(BalancesScreen.class);totals("$0.00","$2.00");
            openExpense("UI expense");click(R.id.expense_delete);positiveDialog();activity(BalancesScreen.class);totals("$0.00","$5.00");
            String totals=group("Separate totals",1,2,3).groupId();
            expense(totals,"Bob share",2000,0,0,1);expense(totals,"Carol share",1000,0,0,2);expense(totals,"David paid",800,3,0,3);
            launch(totals);totals("$4.00","$15.00");capture("separate-totals");
            String other=group("Other group",1).groupId();expense(other,"Unrelated expense",20000,1,0,1);
            launch(other);totals("$100.00","$0.00");launch(totals);totals("$4.00","$15.00");
        } finally {
            instrumentation.runOnMainSync(() -> {
                ((SplitSmartApplication)instrumentation.getTargetContext().getApplicationContext()).getContainer().getFirebaseClients().getAuth().signOut();
                for(Activity a:new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)))a.finish();
            });
        }
    }
}
