package edu.usc.splitsmart.presentation.balances;

import androidx.lifecycle.*;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import edu.usc.splitsmart.domain.calculation.BalanceCalculator;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.*;

/** Foreground revision hints trigger coherent ledger reads; concurrent hints coalesce into one follow-up. */
public final class BalancesViewModel extends FeatureViewModel {
    public record Content(String groupName, BalanceSummary summary, Map<String,String> names, List<Settlement> settlements, List<Expense> expenses) { }
    public final MutableLiveData<Content> content=new MutableLiveData<>();
    private final SettlementService settlements;
    private final GroupService groups;
    private final GroupUpdatesService updates;
    private final Executor worker;
    private final Executor delivery;
    private final String groupId;
    private GroupObservation subscription;
    private int generation;
    private boolean active;
    private boolean refreshAgain;
    private long requestedRevision;
    private String viewer;
    private boolean ownsWorker;
    public BalancesViewModel(SavedStateHandle state, SettlementService settlements, GroupService groups,
            GroupUpdatesService updates, String groupId) {
        this(state,settlements,groups,updates,Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"balance-overview");t.setDaemon(true);return t;}),mainExecutor(),groupId);
        ownsWorker=true;
    }
    private static Executor mainExecutor() {
        Handler handler=new Handler(Looper.getMainLooper());return command -> handler.post(command);
    }
    public BalancesViewModel(SavedStateHandle state, SettlementService settlements, GroupService groups,
            GroupUpdatesService updates, Executor worker, Executor delivery, String groupId) {
        super(state);this.settlements=settlements;this.groups=groups;this.updates=updates;
        this.worker=worker;this.delivery=delivery;this.groupId=groupId;
    }
    public void activate(String uid) {
        if(active && Objects.equals(viewer,uid)) return;
        clear();bindUser(uid);viewer=uid;active=true;refresh();
    }
    private void subscribe() {
        if(subscription!=null)return;
        subscription=updates.observe(groupId,new GroupRevisionObserver() {
            @Override public void onRevision(long revision) {
                if(!active)return;
                requestedRevision=Math.max(requestedRevision,revision);
                Content current=content.getValue();
                if(current!=null && revision<=current.summary().revision())return;
                if(!isBusy())refresh();
            }
            @Override public void onError(AppError error) {
                if(!active)return;
                if(subscription!=null)subscription.cancel();subscription=null;
                ++generation;cancelOperations();content.setValue(null);failed(error);
            }
        });
    }
    public void refresh() {
        if(!active || viewer==null)return;
        if(isBusy()) {refreshAgain=true;return;}
        cancelOperations();int read=++generation;String actor=viewer;
        content.setValue(null);start();subscribe();
        track(groups.getGroup(groupId,ReadPolicy.FRESH,new Callback<>() {
            @Override public void onSuccess(ReadResult<Group> group) {
                if(!current(read,actor))return;
                track(settlements.getLedger(groupId,ReadPolicy.FRESH,new Callback<>() {
                    @Override public void onSuccess(ReadResult<LedgerSnapshot> result) {
                        worker.execute(() -> {
                            try {
                                LedgerSnapshot ledger=result.value();
                                if(!groupId.equals(ledger.groupId()))throw new IllegalArgumentException("Wrong ledger scope");
                                BalanceSummary summary=BalanceCalculator.calculate(ledger,actor);
                                delivery.execute(() -> loadNames(read,actor,group.value().name(),summary,ledger.settlements(),ledger.expenses()));
                            } catch(ArithmeticException error) {
                                delivery.execute(() -> fail(read,new AppError(ErrorCode.OUT_OF_RANGE,"Balances exceed the supported amount range.",null,false)));
                            } catch(IllegalArgumentException error) {
                                delivery.execute(() -> fail(read,new AppError(ErrorCode.INTERNAL,"Could not read a complete group ledger.",null,false)));
                            }
                        });
                    }
                    @Override public void onError(AppError error) {fail(read,error);}
                }));
            }
            @Override public void onError(AppError error) {fail(read,error);}
        }));
    }
    private void loadNames(int read,String actor,String name,BalanceSummary summary,List<Settlement> payments,List<Expense> expenses) {
        if(!current(read,actor))return;
        track(groups.getMemberProfiles(groupId,ReadPolicy.FRESH,new Callback<>() {
            @Override public void onSuccess(ReadResult<List<MemberProfile>> result) {
                if(!current(read,actor))return;
                Map<String,String> names=new HashMap<>();
                for(MemberProfile member:result.value())names.put(member.uid(),member.name());
                List<Settlement> ordered=new ArrayList<>(payments);
                ordered.sort(Comparator.comparing(Settlement::createdAt).thenComparing(Settlement::settlementId).reversed());
                List<Expense> orderedExpenses=new ArrayList<>(expenses);
                orderedExpenses.sort(Comparator.comparing(Expense::createdAt).thenComparing(Expense::expenseId).reversed());
                content.setValue(new Content(name,summary,Map.copyOf(names),List.copyOf(ordered),List.copyOf(orderedExpenses)));done("");
                boolean repeat=refreshAgain || requestedRevision>summary.revision();refreshAgain=false;
                if(repeat)refresh();
            }
            @Override public void onError(AppError error) {fail(read,error);}
        }));
    }
    private boolean current(int read,String actor) {return active && read==generation && Objects.equals(viewer,actor) && subscription!=null && subscription.isCurrent(actor);}
    private void fail(int read,AppError error) {
        if(!active || read!=generation)return;
        refreshAgain=false;content.setValue(null);failed(error);
    }
    public void pause() {
        active=false;++generation;
        if(subscription!=null)subscription.cancel();subscription=null;
        cancelOperations();refreshAgain=false;
    }
    public void clear() {pause();viewer=null;requestedRevision=0;content.setValue(null);}
    @Override protected void onCleared() {pause();if(ownsWorker)((ExecutorService)worker).shutdownNow();super.onCleared();}
}
