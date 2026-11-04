package edu.usc.splitsmart.data.firebase;

import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import edu.usc.splitsmart.data.repository.GroupUpdatesRepository;
import edu.usc.splitsmart.domain.contract.*;

/** Observes only the authorized parent revision; never constructs financial snapshots from listeners. */
public final class FirebaseGroupUpdatesRepository implements GroupUpdatesRepository {
    private final FirebaseRepositoryContext context;
    public FirebaseGroupUpdatesRepository(FirebaseRepositoryContext context) {this.context=context;}
    @Override public GroupObservation observe(String groupId, GroupRevisionObserver observer) {
        Subscription subscription=new Subscription(observer);
        if(subscription.uid==null) {
            subscription.error(new AppError(ErrorCode.UNAUTHENTICATED,"Sign in to view balances.",null,false));
        } else {
            ListenerRegistration registration=context.db.collection("groups").document(groupId)
                .addSnapshotListener(MetadataChanges.INCLUDE,(snapshot,error) -> {
                    if(error!=null) {subscription.error(FirebaseErrors.map(error));return;}
                    if(snapshot==null || snapshot.getMetadata().isFromCache()) return;
                    if(!snapshot.exists()) {
                        subscription.error(new AppError(ErrorCode.NOT_FOUND,"This group no longer exists.",null,false));return;
                    }
                    try {
                        IdentityCodec.version(snapshot.getData());
                        if(!"ACTIVE".equals(snapshot.getString("status"))) {
                            subscription.error(new AppError(ErrorCode.GROUP_DELETING,"This group is being deleted.",null,false));return;
                        }
                        long revision=IdentityCodec.integer(snapshot.get("revision"));
                        if(revision<1 || revision>ContractVersion.MAX_REVISION) throw new IllegalArgumentException("Invalid revision");
                        subscription.deliver(() -> observer.onRevision(revision),false);
                    } catch(RuntimeException invalid) {
                        subscription.error(new AppError(ErrorCode.INTERNAL,"Could not observe this group.",null,false));
                    }
                });
            subscription.attach(registration);
        }
        return subscription;
    }
    private final class Subscription implements GroupObservation {
        private final Object lock=new Object();
        private final GroupRevisionObserver observer;
        private final String uid=context.scope.uid();
        private final long epoch=context.scope.generation();
        private ListenerRegistration registration;
        private boolean cancelled;
        Subscription(GroupRevisionObserver observer) {this.observer=observer;}
        void attach(ListenerRegistration next) {
            boolean remove;
            synchronized(lock) {remove=cancelled;if(!remove)registration=next;}
            if(remove)next.remove();
        }
        void error(AppError error) {deliver(() -> observer.onError(error),true);}
        void deliver(Runnable action, boolean terminal) {
            context.delivery.execute(() -> {
                boolean obsolete;
                synchronized(lock) {
                    if(cancelled)return;
                    obsolete=uid!=null && !context.scope.current(uid,epoch);
                    if(!obsolete)action.run();
                }
                if(obsolete || terminal)cancel();
            });
        }
        @Override public boolean isCurrent(String expectedUid) {
            synchronized(lock) {return !cancelled && uid!=null && uid.equals(expectedUid) && context.scope.current(uid,epoch);}
        }
        @Override public void cancel() {
            ListenerRegistration prior;
            synchronized(lock) {cancelled=true;prior=registration;registration=null;}
            if(prior!=null)prior.remove();
        }
    }
}
