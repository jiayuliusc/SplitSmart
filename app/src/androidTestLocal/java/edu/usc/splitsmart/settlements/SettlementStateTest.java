package edu.usc.splitsmart.settlements;

import static org.junit.Assert.*;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.settlements.SettlementViewModel;
import edu.usc.splitsmart.service.*;

@RunWith(AndroidJUnit4.class)
public final class SettlementStateTest {
    private static final class Harness {
        final List<List<Object>> writes = new ArrayList<>();
        Callback<MutationResult<Settlement>> callback;
        int reads;
        final SettlementService settlements = (SettlementService) Proxy.newProxyInstance(SettlementService.class.getClassLoader(),new Class<?>[]{SettlementService.class},(p,m,a) -> {
            if (m.getName().equals("getLedger")) {
                reads++;
                @SuppressWarnings("unchecked") var result = (Callback<ReadResult<LedgerSnapshot>>) a[2];
                var expense = new Expense("e","g","Dinner",2000,"USD","a","a",List.of("a","b"),SplitMethod.EQUAL,Category.FOOD,null,LocalDate.of(2026,11,4),Instant.EPOCH,Instant.EPOCH,List.of(new ExpenseShare("a",1000,null),new ExpenseShare("b",1000,null)));
                result.onSuccess(new ReadResult<>(new LedgerSnapshot("g",reads,List.of("a","b"),List.of(expense),List.of()),ReadSource.SERVER));
            } else if (m.getName().equals("createSettlement")) {
                writes.add(new ArrayList<>(Arrays.asList(a).subList(0,5)));
                @SuppressWarnings("unchecked") var result = (Callback<MutationResult<Settlement>>) a[5]; callback=result;
            } else throw new AssertionError(m.getName());
            return (AsyncOperation) () -> {};
        });
        final GroupService groups = (GroupService) Proxy.newProxyInstance(GroupService.class.getClassLoader(),new Class<?>[]{GroupService.class},(p,m,a) -> {
            assertEquals("getMemberProfiles",m.getName());
            @SuppressWarnings("unchecked") var result = (Callback<ReadResult<List<MemberProfile>>>) a[2];
            result.onSuccess(new ReadResult<>(List.of(new MemberProfile("a","Alice","a@usc.edu"),new MemberProfile("b","Bob","b@usc.edu")),ReadSource.SERVER));
            return (AsyncOperation) () -> {};
        });
    }
    @Test public void unknownOutcomeRetriesIdenticalIntentAfterRecreation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            var harness = new Harness(); var state = new SavedStateHandle();
            var model = new SettlementViewModel(state,harness.settlements,harness.groups,"g",null);
            model.bindUser("b"); model.load(); model.save("a","4.00","First");
            harness.callback.onError(new AppError(ErrorCode.UNAVAILABLE,"Unavailable",null,true));
            assertTrue(model.pending()); model.save("a","5.00","Modified"); assertEquals(1,harness.writes.size());
            var restored = new SettlementViewModel(state,harness.settlements,harness.groups,"g",null);
            restored.bindUser("b"); restored.retry();
            assertEquals(harness.writes.get(0),harness.writes.get(1));
        });
    }
    @Test public void conflictRefreshRequiresExplicitSaveAndCreatesNewIntent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            var harness = new Harness(); var model = new SettlementViewModel(new SavedStateHandle(),harness.settlements,harness.groups,"g",null);
            model.bindUser("b"); model.load(); model.editDraft("amount","4.00"); model.save("a","4.00",null);
            harness.callback.onError(new AppError(ErrorCode.CONFLICT,"Conflict",null,false));
            assertFalse(model.pending()); assertEquals(2,harness.reads); assertEquals(1,harness.writes.size());
            assertEquals("4.00",model.draft("amount",""));
            model.save("a","4.00",null); assertEquals(2,harness.writes.size());
            assertNotEquals(harness.writes.get(0).get(4),harness.writes.get(1).get(4));
            assertEquals(2L,harness.writes.get(1).get(1));
        });
    }
}
