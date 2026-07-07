package com.yowyob.tiibntick.core.sync.application.port.out;

import com.yowyob.tiibntick.core.sync.domain.event.SyncDomainEvent;
import reactor.core.publisher.Mono;

public interface ISyncEventPublisher {
    Mono<Void> publish(SyncDomainEvent event);
}
