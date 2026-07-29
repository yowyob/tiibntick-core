package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.RelayDepositRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain RelayDepositRepository port to the R2DBC
 * Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class RelayDepositRepositoryAdapter implements RelayDepositRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.RelayDepositRepository r2dbcRepository;

    @Override
    public Mono<RelayDeposit> save(RelayDeposit relayDeposit) {
        return r2dbcRepository.save(relayDeposit);
    }

    @Override
    public Mono<RelayDeposit> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Flux<RelayDeposit> findByRelayPointId(UUID relayPointId) {
        return r2dbcRepository.findByRelayPointId(relayPointId);
    }

    @Override
    public Flux<RelayDeposit> findByClientId(UUID clientId) {
        return r2dbcRepository.findByClientId(clientId);
    }

    @Override
    public Flux<RelayDeposit> findByStatus(RelayDepositStatus status) {
        return r2dbcRepository.findByStatus(status);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
