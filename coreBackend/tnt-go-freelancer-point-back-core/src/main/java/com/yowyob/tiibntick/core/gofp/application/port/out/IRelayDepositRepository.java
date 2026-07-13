package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayDepositEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IRelayDepositRepository {
    Mono<RelayDepositEntity> save(RelayDepositEntity entity);
    Mono<RelayDepositEntity> findById(UUID id);
    Flux<RelayDepositEntity> findByRelayHubId(UUID relayHubId);
    Flux<RelayDepositEntity> findByClientActorId(UUID clientActorId);
    Flux<RelayDepositEntity> findByPacketId(UUID packetId);
}
