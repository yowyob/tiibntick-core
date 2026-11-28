package com.yowyob.tiibntick.core.delivery.domain.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * An aggregate of delivery-core that records events for publication after its save (outbox, same transaction).
 */
public interface EventSource {

    /** @return the events recorded since the last {@link #clearDomainEvents()}, in recording order */
    List<DeliveryDomainEvent> getDomainEvents();

    /** Forgets the recorded events (after they were handed to the publisher). */
    void clearDomainEvents();

    /**
     * Collects the events of several aggregates sharing objects (a delivery and its parcel, an announcement and the
     * same parcel), each event once, in order, then clears every source.
     */
    static List<DeliveryDomainEvent> drain(EventSource... sources) {
        Set<DeliveryDomainEvent> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<DeliveryDomainEvent> all = new ArrayList<>();
        for (EventSource source : sources) {
            if (source == null) {
                continue;
            }
            for (DeliveryDomainEvent event : source.getDomainEvents()) {
                if (seen.add(event)) {
                    all.add(event);
                }
            }
        }
        for (EventSource source : sources) {
            if (source != null) {
                source.clearDomainEvents();
            }
        }
        return all;
    }
}
