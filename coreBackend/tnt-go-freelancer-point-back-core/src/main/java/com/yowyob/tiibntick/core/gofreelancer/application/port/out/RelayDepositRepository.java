package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reading relay deposits. They are stored by {@code tnt-inventory-core} (hub package entries);
 * this port only reads them as {@link RelayDeposit} views.
 */
public interface RelayDepositRepository {
    Mono<RelayDeposit> findById(UUID id);
    Flux<RelayDeposit> findByRelayPointId(UUID relayPointId);
    Flux<RelayDeposit> findByClientId(UUID clientId);
}
