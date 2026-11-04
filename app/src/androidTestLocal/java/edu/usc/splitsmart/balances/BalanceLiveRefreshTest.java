package edu.usc.splitsmart.balances;

import static org.junit.Assert.*;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.service.*;
import edu.usc.splitsmart.presentation.balances.BalancesViewModel;

@RunWith(AndroidJUnit4.class)
public final class BalanceLiveRefreshTest {
    @Test public void revisionDuringReadIsCoalescedAndStoppedCallbacksCannotExposeData() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Queue<Runnable> queued=new ArrayDeque<>(); Executor delivery=queued::add;
            GroupRevisionObserver[] observer={null}; boolean[] detached={false}; boolean[] scopeCurrent={true};
            List<Callback<ReadResult<LedgerSnapshot>>> reads=new ArrayList<>();
            GroupService groups=(GroupService)Proxy.newProxyInstance(GroupService.class.getClassLoader(),new Class<?>[]{GroupService.class},(p,m,a) -> {
                if(m.getName().equals("getGroup")) {
                    @SuppressWarnings("unchecked") var callback=(Callback<ReadResult<Group>>)a[2];
                    callback.onSuccess(new ReadResult<>(new Group("g","Roommates",null,"A",List.of("A","B"),1,GroupStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH),ReadSource.SERVER));
                } else if(m.getName().equals("getMemberProfiles")) {
                    @SuppressWarnings("unchecked") var callback=(Callback<ReadResult<List<MemberProfile>>>)a[2];
                    callback.onSuccess(new ReadResult<>(List.of(new MemberProfile("A","Alice","a@usc.edu"),new MemberProfile("B","Bob","b@usc.edu")),ReadSource.SERVER));
                } else throw new AssertionError(m.getName());
                return (AsyncOperation)() -> {};
            });
            SettlementService settlements=(SettlementService)Proxy.newProxyInstance(SettlementService.class.getClassLoader(),new Class<?>[]{SettlementService.class},(p,m,a) -> {
                assertEquals("getLedger",m.getName());
                @SuppressWarnings("unchecked") var callback=(Callback<ReadResult<LedgerSnapshot>>)a[2]; reads.add(callback);
                return (AsyncOperation)() -> {};
            });
            GroupUpdatesService updates=(id,callback) -> {observer[0]=callback;return new GroupObservation(){public void cancel(){detached[0]=true;}public boolean isCurrent(String uid){return !detached[0] && scopeCurrent[0] && uid.equals("A");}};};
            var model=new BalancesViewModel(new SavedStateHandle(),settlements,groups,updates,Runnable::run,delivery,"g");
            model.activate("A"); assertEquals(1,reads.size());
            observer[0].onRevision(2); observer[0].onRevision(3); assertEquals(1,reads.size());
            reads.get(0).onSuccess(new ReadResult<>(new LedgerSnapshot("g",1,List.of("A","B"),List.of(),List.of()),ReadSource.SERVER));
            while(!queued.isEmpty()) queued.remove().run();
            assertEquals("Latest revision causes one follow-up read",2,reads.size());
            reads.get(1).onSuccess(new ReadResult<>(new LedgerSnapshot("g",3,List.of("A","B"),List.of(),List.of()),ReadSource.SERVER));
            while(!queued.isEmpty()) queued.remove().run();
            assertNotNull(model.content.getValue()); assertEquals(3,model.content.getValue().summary().revision());
            observer[0].onRevision(3); assertEquals(2,reads.size());
            observer[0].onRevision(4);assertEquals(3,reads.size());
            scopeCurrent[0]=false; // SDK account changed before its queued UI notification.
            reads.get(2).onSuccess(new ReadResult<>(new LedgerSnapshot("g",4,List.of("A","B"),List.of(),List.of()),ReadSource.SERVER));
            while(!queued.isEmpty())queued.remove().run();
            assertNull("Late private worker output is suppressed",model.content.getValue());
            model.pause(); assertTrue(detached[0]);
            observer[0].onRevision(5); assertEquals(3,reads.size());
            model.clear(); assertNull(model.content.getValue());
        });
    }
}
