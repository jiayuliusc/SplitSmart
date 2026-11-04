package edu.usc.splitsmart.balances;

import static org.junit.Assert.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.Test;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.data.repository.LedgerRepository;
import edu.usc.splitsmart.service.AuthService;
import edu.usc.splitsmart.service.balances.DefaultBalanceService;

public final class BalanceServiceTest {
    private final Queue<Runnable> main = new ArrayDeque<>();
    private final Queue<Runnable> work = new ArrayDeque<>();
    private SessionObserver observer;
    private boolean cancelled;
    private String currentUid="A";
    private ReadPolicy policy;
    private final AuthService auth = (AuthService) Proxy.newProxyInstance(AuthService.class.getClassLoader(), new Class<?>[]{AuthService.class}, (p,m,a) -> {
        if (!m.getName().equals("observeSession")) throw new AssertionError(m.getName());
        observer = (SessionObserver)a[0];
        return (AsyncOperation) () -> cancelled = true;
    });
    private final LedgerRepository ledger = (id,p,callback) -> {
        policy = p;
        callback.onSuccess(new ReadResult<>(new LedgerSnapshot(id,3,List.of("A","B"),List.of(),List.of()),ReadSource.CACHE));
        return () -> {};
    };
    private final DefaultBalanceService service = new DefaultBalanceService(auth,ledger,work::add,main::add,uid -> uid.equals(currentUid));
    private static final class Result implements Callback<ReadResult<BalanceSummary>> {
        ReadResult<BalanceSummary> value; AppError error; int calls;
        public void onSuccess(ReadResult<BalanceSummary> v) {value=v; calls++;}
        public void onError(AppError e) {error=e; calls++;}
    }
    private void drain(Queue<Runnable> queue) { while (!queue.isEmpty()) queue.remove().run(); }
    @Test public void computationAndDeliveryAreQueuedAndPreserveReadSource() {
        Result r = new Result(); service.getBalances("g",ReadPolicy.CACHE_ONLY,r);
        observer.onSessionChanged(new Session("A","a@usc.edu"));
        assertEquals(ReadPolicy.CACHE_ONLY,policy); assertEquals(0,r.calls); assertFalse(work.isEmpty());
        drain(work); assertEquals(0,r.calls); drain(main);
        assertEquals(1,r.calls); assertEquals(ReadSource.CACHE,r.value.source()); assertEquals(3,r.value.value().revision());
        assertEquals("A",r.value.value().viewerId()); assertTrue(cancelled);
    }
    @Test public void signedOutReturnsQueuedErrorWithoutLedgerRead() {
        Result r = new Result(); service.getBalances("g",ReadPolicy.FRESH,r);
        observer.onSessionChanged(new Session(null,null)); assertEquals(0,r.calls); drain(main);
        assertEquals(ErrorCode.UNAUTHENTICATED,r.error.code()); assertNull(policy); assertTrue(cancelled);
    }
    @Test public void sessionSwitchSuppressesQueuedPrivateResult() {
        Result r = new Result(); service.getBalances("g",ReadPolicy.FRESH,r);
        observer.onSessionChanged(new Session("A","a@usc.edu")); drain(work);
        observer.onSessionChanged(new Session("B","b@usc.edu")); drain(main);
        assertEquals(0,r.calls); assertTrue(cancelled);
    }
    @Test public void changedSdkUserSuppressesDeliveryBeforeObserverNotification() {
        Result r=new Result(); service.getBalances("g",ReadPolicy.FRESH,r);
        observer.onSessionChanged(new Session("A","a@usc.edu")); drain(work);
        currentUid="B"; // SDK changes first; its queued observer event has not run yet.
        drain(main); assertEquals(0,r.calls); assertTrue(cancelled);
    }
    @Test public void cancellationSuppressesCalculationDeliveryAndCancelsSessionObserver() {
        Result r = new Result(); AsyncOperation operation=service.getBalances("g",ReadPolicy.FRESH,r);
        observer.onSessionChanged(new Session("A","a@usc.edu")); operation.cancel(); drain(work); drain(main);
        assertEquals(0,r.calls); assertTrue(cancelled);
    }
    @Test public void ledgerFailureIsPreserved() {
        AppError failure = new AppError(ErrorCode.UNAVAILABLE,"Offline",null,true);
        var failing = new DefaultBalanceService(auth,(id,p,c) -> {c.onError(failure);return () -> {};},work::add,main::add,uid -> uid.equals(currentUid));
        Result r=new Result(); failing.getBalances("g",ReadPolicy.FRESH,r);
        observer.onSessionChanged(new Session("A","a@usc.edu")); drain(main);
        assertEquals(failure,r.error); assertTrue(cancelled);
    }
    @Test public void wrongLedgerScopeIsAnExplicitError() {
        var wrong=new DefaultBalanceService(auth,(id,p,c) -> {
            c.onSuccess(new ReadResult<>(new LedgerSnapshot("other",1,List.of("A"),List.of(),List.of()),ReadSource.SERVER));
            return () -> {};
        },work::add,main::add,uid -> uid.equals(currentUid));
        Result r=new Result(); wrong.getBalances("g",ReadPolicy.FRESH,r);
        observer.onSessionChanged(new Session("A","a@usc.edu")); drain(work); drain(main);
        assertEquals(ErrorCode.INTERNAL,r.error.code()); assertNull(r.value);
    }
    @Test public void aggregateOverflowIsAnExplicitError() {
        var now=java.time.Instant.now(); var date=java.time.LocalDate.now();
        var entries=List.of(new Settlement("s1","g","B","B","A",ContractVersion.MAX_ABSOLUTE_CENTS,"USD",null,date,now,now),
            new Settlement("s2","g","C","C","A",1,"USD",null,date,now,now));
        var overflowing=new DefaultBalanceService(auth,(id,p,c) -> {
            c.onSuccess(new ReadResult<>(new LedgerSnapshot("g",1,List.of("A","B","C"),List.of(),entries),ReadSource.SERVER));
            return () -> {};
        },work::add,main::add,uid -> uid.equals(currentUid));
        Result r=new Result(); overflowing.getBalances("g",ReadPolicy.FRESH,r);
        observer.onSessionChanged(new Session("A","a@usc.edu")); drain(work); drain(main);
        assertEquals(ErrorCode.OUT_OF_RANGE,r.error.code()); assertNull(r.value);
    }
    @Test public void invalidGroupIsQueuedValidationFailure() {
        Result r = new Result(); service.getBalances("",ReadPolicy.FRESH,r); assertEquals(0,r.calls); drain(main);
        assertEquals(ErrorCode.VALIDATION,r.error.code()); assertNull(observer);
    }
}
