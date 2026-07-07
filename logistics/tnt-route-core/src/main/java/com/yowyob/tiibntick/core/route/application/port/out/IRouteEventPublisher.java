package com.yowyob.tiibntick.core.route.application.port.out;

import com.yowyob.tiibntick.core.route.domain.event.*;
import reactor.core.publisher.Mono;

public interface IRouteEventPublisher {
    Mono<Void> publishTourOptimized(TourOptimizedEvent event);
    Mono<Void> publishEtaUpdated(EtaUpdatedEvent event);
    Mono<Void> publishReroutingTriggered(ReroutingTriggeredEvent event);
    Mono<Void> publishVrpFallback(VrpFallbackActivatedEvent event);
}
