package edu.usc.splitsmart.service.groups;

import java.util.concurrent.Executor;
import edu.usc.splitsmart.data.repository.GroupUpdatesRepository;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.service.GroupUpdatesService;
import edu.usc.splitsmart.util.PendingOperation;

public final class DefaultGroupUpdatesService implements GroupUpdatesService {
    private final GroupUpdatesRepository repository;
    private final Executor delivery;
    public DefaultGroupUpdatesService(GroupUpdatesRepository repository, Executor delivery) {
        this.repository=repository; this.delivery=delivery;
    }
    @Override public GroupObservation observe(String groupId, GroupRevisionObserver observer) {
        if(groupId!=null && groupId.matches("[A-Za-z0-9_-]{1,128}")) return repository.observe(groupId,observer);
        PendingOperation<Void> operation=new PendingOperation<>(delivery,new Callback<>() {
            @Override public void onSuccess(Void ignored) { }
            @Override public void onError(AppError error) {observer.onError(error);}
        });
        operation.onError(new AppError(ErrorCode.VALIDATION,"Choose a group to view balances.","groupId",false));
        return new GroupObservation() {
            @Override public void cancel() {operation.cancel();}
            @Override public boolean isCurrent(String uid) {return false;}
        };
    }
}
