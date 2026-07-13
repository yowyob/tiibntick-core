package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.EvaluationEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IEvaluationUseCase {

    /** Le client note le livreur après livraison. */
    Mono<EvaluationEntity> clientRatesFreelancer(UUID deliveryId, UUID clientActorId,
                                                   UUID freelancerActorId, int rating, String comment);

    /** Le livreur note le client après livraison. */
    Mono<EvaluationEntity> freelancerRatesClient(UUID deliveryId, UUID freelancerActorId,
                                                   UUID clientActorId, int rating, String comment);

    Flux<EvaluationEntity> findByDeliveryId(UUID deliveryId);
    Flux<EvaluationEntity> findByEvaluatedActorId(UUID evaluatedActorId);
}
