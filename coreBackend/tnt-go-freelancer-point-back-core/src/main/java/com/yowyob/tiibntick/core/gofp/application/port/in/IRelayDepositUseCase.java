package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayDepositEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IRelayDepositUseCase {

    /** Crée un dépôt de colis dans un point relais. */
    Mono<RelayDepositEntity> createDeposit(UUID packetId, UUID clientActorId,
                                            UUID relayHubId, UUID freelancerActorId,
                                            UUID deliveryId);

    /** Marque le colis comme récupéré. */
    Mono<RelayDepositEntity> markRetrieved(UUID depositId);

    /** Calcule les pénalités de dépassement. */
    Mono<Double> calculatePenalty(UUID depositId);

    Mono<RelayDepositEntity>  findById(UUID id);
    Flux<RelayDepositEntity>  findByRelayHubId(UUID relayHubId);
    Flux<RelayDepositEntity>  findByClientActorId(UUID clientActorId);
}
