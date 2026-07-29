package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface EvaluationRepository extends R2dbcRepository<Evaluation, UUID> {
    Flux<Evaluation> findByEvaluatedId(UUID evaluatedId);
    Flux<Evaluation> findByDeliveryId(UUID deliveryId);
}
