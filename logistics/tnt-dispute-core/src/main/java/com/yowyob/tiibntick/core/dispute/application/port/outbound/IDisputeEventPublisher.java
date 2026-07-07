package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import com.yowyob.tiibntick.core.dispute.domain.event.*;
import reactor.core.publisher.Mono;

/**
 * Secondary port for publishing dispute domain events to Kafka.
 * All events are published to their respective {@code tnt.dispute.*} topics.
 *
 * @author MANFOUO Braun
 */
public interface IDisputeEventPublisher {

    Mono<Void> publishDisputeOpened(DisputeOpened event);

    Mono<Void> publishMediatorAssigned(MediatorAssigned event);

    Mono<Void> publishEvidenceSubmitted(EvidenceSubmitted event);

    Mono<Void> publishDisputeRuled(DisputeRuled event);

    Mono<Void> publishDisputeEscalated(DisputeEscalated event);

    Mono<Void> publishCompensationProcessed(CompensationProcessed event);

    Mono<Void> publishDisputeClosed(DisputeClosed event);

    /**
     * Publishes all uncommitted domain events from the given dispute aggregate.
     * The aggregate's event list is cleared after publishing.
     *
     * @param dispute the dispute with pending events
     * @return a Mono that completes when all events are published
     */
    Mono<Void> publishAll(com.yowyob.tiibntick.core.dispute.domain.model.Dispute dispute);
}
