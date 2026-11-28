package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import reactor.core.publisher.Mono;

/** Publishes the pending changes of a dispute on {@code tnt.dispute.events} through the outbox (lot L4). */
public interface IDisputeAggregateEventPublisher {

    /** Writes one outbox row per pending change of {@code dispute}, in the caller's transaction. */
    Mono<Void> publish(Dispute dispute);
}
