package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayDepositEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface R2dbcRelayDepositRepository
        extends ReactiveCrudRepository<RelayDepositEntity, UUID> {

    Flux<RelayDepositEntity> findByRelayHubId(UUID relayHubId);
    Flux<RelayDepositEntity> findByClientActorId(UUID clientActorId);
    Flux<RelayDepositEntity> findByPacketId(UUID packetId);
}
