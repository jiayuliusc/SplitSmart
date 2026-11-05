package edu.usc.splitsmart.service;

import edu.usc.splitsmart.domain.contract.*;

/** Foreground invalidation hints. Consumers reload the coherent ledger through their service. */
public interface GroupUpdatesService {
    GroupObservation observe(String groupId, GroupRevisionObserver observer);
}
