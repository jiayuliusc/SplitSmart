package edu.usc.splitsmart.balances;

import static org.junit.Assert.*;
import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.balances.BalancesScreen;
import edu.usc.splitsmart.presentation.groups.GroupDetailScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BalancesScreenTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private Activity screen;
    private static final class Result<T> implements Callback<T> {
        final CountDownLatch done=new CountDownLatch(1); T value; AppError error;
        public void onSuccess(T v) {value=v;done.countDown();}
        public void onError(AppError e) {error=e;done.countDown();}
    }
    private <T> T ok(Function<Callback<T>,AsyncOperation> call) throws Exception {
        Result<T> result=new Result<>(); instrumentation.runOnMainSync(() -> call.apply(result));
        assertTrue(result.done.await(30,TimeUnit.SECONDS)); assertNull(String.valueOf(result.error),result.error); return result.value;
    }
    private void await(BooleanSupplier check) throws Exception {
        for (int i=0;i<600;i++) {
            boolean[] done={false}; instrumentation.runOnMainSync(() -> done[0]=check.getAsBoolean());
            if(done[0]) return; Thread.sleep(50);
        }
        fail("Screen did not reach expected state");
    }
    private void activity(Class<?> type) throws Exception {
        await(() -> {
            for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if(a.getClass()==type && a.findViewById(R.id.feature_content).isShown()) { screen=a; return true; }
            }
            return false;
        });
    }
    private void amount(String value) throws Exception {
        await(() -> screen.findViewById(R.id.balances_data).isShown()
            && ((TextView)screen.findViewById(R.id.balances_receive_amount)).getText().toString().equals(value));
    }
    private void click(int id) { instrumentation.runOnMainSync(() -> screen.findViewById(id).performClick()); }
    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().waitForIdle(150,5000);
        Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try(FileOutputStream out=new FileOutputStream(new File(instrumentation.getTargetContext().getExternalFilesDir(null),name+".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
        }
        bitmap.recycle();
        // Gradle uninstalls the APK after tests; retain synthetic screenshots until host retrieval.
        String source=new File(instrumentation.getTargetContext().getExternalFilesDir(null),name+".png").getAbsolutePath();
        try (InputStream result=new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand("cp "+source+" /sdcard/Download/splitsmart-"+name+".png"))) {
            byte[] buffer=new byte[256]; while(result.read(buffer)!=-1) { /* wait for the copy */ }
        }
    }
    @Test public void balanceNavigationRefreshEditsDeletionAndRotation() throws Exception {
        var app=(SplitSmartApplication)instrumentation.getTargetContext().getApplicationContext();
        var auth=app.getContainer().auth(); var groups=app.getContainer().groups(); var expenses=app.getContainer().expenses();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String ownerEmail="balance-owner-"+suffix+"@usc.edu", memberEmail="balance-member-"+suffix+"@usc.edu";
        long numeric=Math.floorMod(UUID.randomUUID().getMostSignificantBits(),9_000_000_000L);
        String ownerId=String.format(Locale.ROOT,"%010d",numeric), memberId=String.format(Locale.ROOT,"%010d",numeric+1);
        try {
            ok(auth::signOut);
            MutationResult<User> owner=ok(cb -> auth.register(ownerEmail,"balance-password",new ProfileDraft("Alex Morgan",ownerId),UUID.randomUUID().toString(),cb));
            MutationResult<Group> created=ok(cb -> groups.createGroup(new GroupDraft("Parkside roommates",null),UUID.randomUUID().toString(),cb));
            String groupId=created.value().groupId(); ok(auth::signOut);
            MutationResult<User> member=ok(cb -> auth.register(memberEmail,"balance-password",new ProfileDraft("Sam Rivera",memberId),UUID.randomUUID().toString(),cb));
            ok(auth::signOut); this.<Session>ok(cb -> auth.signIn(ownerEmail,"balance-password",cb));
            MutationResult<Group> added=ok(cb -> groups.addMember(groupId,1,memberEmail,UUID.randomUUID().toString(),cb));
            List<String> ids=List.of(owner.value().uid(),member.value().uid());
            ExpenseDraft draft=new ExpenseDraft("Groceries",3000,owner.value().uid(),ids,SplitMethod.EQUAL,Category.FOOD,null,List.of());
            MutationResult<Expense> expense=ok(cb -> expenses.createExpense(groupId,added.groupRevision(),draft,LocalDate.now(),UUID.randomUUID().toString(),cb));
            instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),GroupDetailScreen.class)
                .putExtra(NavigationContract.GROUP_ID,groupId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activity(GroupDetailScreen.class); click(R.id.groups_balances); activity(BalancesScreen.class); amount("$15.00");
            instrumentation.runOnMainSync(() -> {
                assertEquals("$0.00",((TextView)screen.findViewById(R.id.balances_owe_amount)).getText().toString());
                ViewGroup rows=screen.findViewById(R.id.balances_rows); assertEquals(1,rows.getChildCount());
                assertEquals("Sam Rivera",((TextView)rows.getChildAt(0).findViewById(R.id.balance_member_name)).getText().toString());
            });
            capture("balances-populated");
            ExpenseDraft updated=new ExpenseDraft("Groceries",4000,owner.value().uid(),ids,SplitMethod.EQUAL,Category.FOOD,null,List.of());
            MutationResult<Expense> edited=ok(cb -> expenses.updateExpense(groupId,expense.groupRevision(),expense.value().expenseId(),updated,UUID.randomUUID().toString(),cb));
            click(R.id.balances_refresh); amount("$20.00");
            Activity prior=screen; instrumentation.runOnMainSync(prior::recreate);
            await(() -> !ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(prior));
            activity(BalancesScreen.class); amount("$20.00");
            ExpenseDraft largeDraft=new ExpenseDraft("Annual costs",ContractVersion.MAX_ABSOLUTE_CENTS,owner.value().uid(),ids,SplitMethod.EQUAL,Category.HOUSING,null,List.of());
            MutationResult<Expense> largest=ok(cb -> expenses.updateExpense(groupId,edited.groupRevision(),expense.value().expenseId(),largeDraft,UUID.randomUUID().toString(),cb));
            long share=largest.value().shares().stream().filter(s -> s.participantId().equals(member.value().uid())).findFirst().orElseThrow().amountCents();
            String largeAmount=java.text.NumberFormat.getCurrencyInstance(Locale.US).format(java.math.BigDecimal.valueOf(share,2));
            click(R.id.balances_refresh); amount(largeAmount); capture("balances-large-amount");
            this.<MutationResult<DeletedRecord>>ok(cb -> expenses.deleteExpense(groupId,largest.groupRevision(),expense.value().expenseId(),UUID.randomUUID().toString(),cb));
            click(R.id.balances_refresh); amount("$0.00");
            await(() -> screen.findViewById(R.id.balances_empty).isShown()); capture("balances-settled");
            click(R.id.feature_back); activity(GroupDetailScreen.class);
        } finally {
            instrumentation.runOnMainSync(() -> {
                app.getContainer().getFirebaseClients().getAuth().signOut();
                for(Activity a:new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))) a.finish();
            });
        }
    }
    @Test public void missingGroupDoesNotShowFinancialContent() throws Exception {
        Activity missing=instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),BalancesScreen.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                assertFalse(missing.findViewById(R.id.feature_content).isShown());
                assertFalse(missing.findViewById(R.id.balances_data).isShown());
                assertEquals(View.GONE,missing.findViewById(R.id.access_retry).getVisibility());
                assertFalse(((TextView)missing.findViewById(R.id.access_status)).getText().toString().isEmpty());
            });
        } finally { instrumentation.runOnMainSync(missing::finish); }
    }
    @Test public void fiveHundredTransactionCalculationBenchmark() {
        var now=java.time.Instant.now();
        List<Expense> entries=new ArrayList<>();
        for(int i=0;i<500;i++) entries.add(new Expense("e"+i,"g","Groceries",1000,"USD","A","A",
            List.of("A","B","C"),SplitMethod.EQUAL,Category.FOOD,null,LocalDate.now(),now,now,
            List.of(new ExpenseShare("A",334,null),new ExpenseShare("B",333,null),new ExpenseShare("C",333,null))));
        var ledger=new LedgerSnapshot("g",1,List.of("A","B","C"),entries,List.of());
        for(int i=0;i<10;i++) edu.usc.splitsmart.domain.calculation.BalanceCalculator.calculate(ledger,"A");
        long[] times=new long[20];
        for(int i=0;i<times.length;i++) {
            long start=android.os.SystemClock.elapsedRealtimeNanos();
            var summary=edu.usc.splitsmart.domain.calculation.BalanceCalculator.calculate(ledger,"A");
            times[i]=android.os.SystemClock.elapsedRealtimeNanos()-start;
            assertEquals(333000,summary.totalToReceiveCents());
        }
        Arrays.sort(times);
        android.util.Log.i("BalanceBenchmark","500 expenses, 3 members, 10 warmups, 20 samples: median="
            +times[10]/1_000_000.0+"ms, p95="+times[18]/1_000_000.0+"ms");
        assertTrue("500-transaction calculation p95 must be below 250ms",times[18]<250_000_000L);
    }

}
