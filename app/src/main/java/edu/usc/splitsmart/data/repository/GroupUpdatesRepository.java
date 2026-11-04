package edu.usc.splitsmart.data.repository;

import edu.usc.splitsmart.domain.contract.*;

/** Member-authorized group invalidation stream; cancellation detaches the listener. */
public interface GroupUpdatesRepository {
    GroupObservation observe(String groupId, GroupRevisionObserver observer);
}
