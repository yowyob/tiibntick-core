package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for evaluation persistence operations.
 */
public interface EvaluationRepository {

    Mono<Evaluation> save(Evaluation evaluation);

    Mono<Evaluation> findById(UUID id);

    Flux<Evaluation> findByEvaluatedId(UUID evaluatedId);

    Flux<Evaluation> findByDeliveryId(UUID deliveryId);

    Mono<Void> deleteById(UUID id);
}
