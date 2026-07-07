package com.yowyob.tiibntick.core.incident.port.outbound;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import reactor.core.publisher.Mono;
/**
 * Outbound port: publish incident domain events to Kafka topics.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IIncidentEventPublisher {
    Mono<Void> publish(IncidentCreatedEvent event);
    Mono<Void> publish(IncidentStatusChangedEvent event);
    Mono<Void> publish(IncidentTriagedEvent event);
    Mono<Void> publish(IncidentDriverAssignedEvent event);
    Mono<Void> publish(HandoverCompletedEvent event);
    Mono<Void> publish(IncidentResolvedEvent event);
    Mono<Void> publish(IncidentClosedEvent event);
    Mono<Void> publish(IncidentCancelledEvent event);
    Mono<Void> publish(IncidentEscalatedEvent event);
    Mono<Void> publish(IncidentEscalatedToDisputeEvent event);
    Mono<Void> publish(InterAgencyCoopRequestedEvent event);
    Mono<Void> publish(InterAgencyCoopCompletedEvent event);
}
