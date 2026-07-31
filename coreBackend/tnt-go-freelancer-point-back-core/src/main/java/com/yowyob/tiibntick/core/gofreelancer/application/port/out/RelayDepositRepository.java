package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for relay deposit persistence operations.
 */
public interface RelayDepositRepository {

    Mono<RelayDeposit> save(RelayDeposit relayDeposit);

    Mono<RelayDeposit> findById(UUID id);

    Flux<RelayDeposit> findByRelayPointId(UUID relayPointId);

    Flux<RelayDeposit> findByClientId(UUID clientId);

    Flux<RelayDeposit> findByStatus(RelayDepositStatus status);

    Mono<Void> deleteById(UUID id);
}
