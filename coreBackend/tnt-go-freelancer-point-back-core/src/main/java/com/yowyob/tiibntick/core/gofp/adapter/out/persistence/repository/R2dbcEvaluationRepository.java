package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.EvaluationEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcEvaluationRepository
        extends ReactiveCrudRepository<EvaluationEntity, UUID> {

    Flux<EvaluationEntity> findByDeliveryId(UUID deliveryId);
    Flux<EvaluationEntity> findByEvaluatedActorId(UUID evaluatedActorId);
    Mono<EvaluationEntity> findByDeliveryIdAndEvaluationType(UUID deliveryId, String evaluationType);
}
