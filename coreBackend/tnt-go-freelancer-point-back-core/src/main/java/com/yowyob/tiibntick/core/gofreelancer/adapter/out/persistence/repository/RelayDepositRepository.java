package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface RelayDepositRepository extends ReactiveCrudRepository<RelayDeposit, UUID> {
    /** logistics_id column maps to relayPointId field */
    Flux<RelayDeposit> findByRelayPointId(UUID relayPointId);
    Flux<RelayDeposit> findByClientId(UUID clientId);
    Flux<RelayDeposit> findByStatus(com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus status);
}
