package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain EvaluationRepository port to the R2DBC
 * Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class EvaluationRepositoryAdapter implements EvaluationRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.EvaluationRepository r2dbcRepository;

    @Override
    public Mono<Evaluation> save(Evaluation evaluation) {
        return r2dbcRepository.save(evaluation);
    }

    @Override
    public Mono<Evaluation> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Flux<Evaluation> findByEvaluatedId(UUID evaluatedId) {
        return r2dbcRepository.findByEvaluatedId(evaluatedId);
    }

    @Override
    public Flux<Evaluation> findByDeliveryId(UUID deliveryId) {
        return r2dbcRepository.findByDeliveryId(deliveryId);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
