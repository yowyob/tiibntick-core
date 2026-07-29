package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointSubscription;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface RelayPointSubscriptionR2dbcRepository extends ReactiveCrudRepository<RelayPointSubscription, UUID> {
    Mono<RelayPointSubscription> findByRelayPointId(UUID relayPointId);
}
