package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointSubscription;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RelayPointSubscriptionRepository {
    Mono<RelayPointSubscription> save(RelayPointSubscription subscription);
    Mono<RelayPointSubscription> findById(UUID id);
    Mono<RelayPointSubscription> findByRelayPointId(UUID relayPointId);
    Mono<Void> deleteById(UUID id);
}
