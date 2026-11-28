package com.yowyob.tiibntick.core.incident.port.outbound;

import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import reactor.core.publisher.Mono;

/**
 * Publishes the pending changes of an incident on {@code tnt.incident.events} (lot L3, spec §3.5), through the outbox,
 * in the transaction that saves the incident.
 */
public interface IIncidentAggregateEventPublisher {

    /** Publishes {@link Incident#getPendingTransitions()} in order (the saved state is the payload). */
    Mono<Void> publish(Incident saved, Incident withPendingTransitions);
}
