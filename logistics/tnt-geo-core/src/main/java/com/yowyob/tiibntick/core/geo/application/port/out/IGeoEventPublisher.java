package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.event.RoadNodeCreatedEvent;
import com.yowyob.tiibntick.core.geo.domain.event.ServiceZoneUpdatedEvent;
import com.yowyob.tiibntick.core.geo.domain.event.TrafficConditionChangedEvent;
import reactor.core.publisher.Mono;

/**
 * Outbound port — publishes geo domain events to the event bus (Kafka via yow-event-kernel).
 *
 * Author: MANFOUO Braun
 */
public interface IGeoEventPublisher {

    Mono<Void> publishTrafficChanged(TrafficConditionChangedEvent event);

    Mono<Void> publishRoadNodeCreated(RoadNodeCreatedEvent event);

    Mono<Void> publishServiceZoneUpdated(ServiceZoneUpdatedEvent event);
}
