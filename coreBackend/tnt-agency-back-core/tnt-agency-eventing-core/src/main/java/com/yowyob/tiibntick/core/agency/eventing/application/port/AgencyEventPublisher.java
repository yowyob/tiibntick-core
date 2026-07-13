package com.yowyob.tiibntick.core.agency.eventing.application.port;

import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Outbound port for publishing agency integration events to the platform event bus.
 *
 * <p>Faithful replacement of the monolith {@code EventPublisherPort}: the ERP is now the sole
 * emitter of agency business events (mission, staff, contract, agency lifecycle, hub).
 */
public interface AgencyEventPublisher {

    Mono<Void> publish(TntDomainEvent event);

    Mono<Void> publishAll(List<TntDomainEvent> events);
}
