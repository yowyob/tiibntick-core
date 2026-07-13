package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.EvaluationEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IEvaluationRepository {
    Mono<EvaluationEntity> save(EvaluationEntity entity);
    Mono<EvaluationEntity> findById(UUID id);
    Flux<EvaluationEntity> findByDeliveryId(UUID deliveryId);
    Flux<EvaluationEntity> findByEvaluatedActorId(UUID evaluatedActorId);
    Mono<EvaluationEntity> findByDeliveryIdAndEvaluationType(UUID deliveryId, String evaluationType);
}
