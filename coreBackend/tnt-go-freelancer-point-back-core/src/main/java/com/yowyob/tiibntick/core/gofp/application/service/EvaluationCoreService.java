package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.EvaluationEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IEvaluationUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.IEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationCoreService implements IEvaluationUseCase {

    private final IEvaluationRepository evaluationRepository;

    private static final String TYPE_CLIENT_RATES_FREELANCER  = "CLIENT_RATES_FREELANCER";
    private static final String TYPE_FREELANCER_RATES_CLIENT  = "FREELANCER_RATES_CLIENT";

    @Override
    public Mono<EvaluationEntity> clientRatesFreelancer(UUID deliveryId, UUID clientActorId,
                                                          UUID freelancerActorId, int rating, String comment) {
        return saveEvaluation(deliveryId, clientActorId, freelancerActorId,
            TYPE_CLIENT_RATES_FREELANCER, rating, comment);
    }

    @Override
    public Mono<EvaluationEntity> freelancerRatesClient(UUID deliveryId, UUID freelancerActorId,
                                                          UUID clientActorId, int rating, String comment) {
        return saveEvaluation(deliveryId, freelancerActorId, clientActorId,
            TYPE_FREELANCER_RATES_CLIENT, rating, comment);
    }

    @Override
    public Flux<EvaluationEntity> findByDeliveryId(UUID deliveryId)              { return evaluationRepository.findByDeliveryId(deliveryId); }
    @Override
    public Flux<EvaluationEntity> findByEvaluatedActorId(UUID evaluatedActorId)  { return evaluationRepository.findByEvaluatedActorId(evaluatedActorId); }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Mono<EvaluationEntity> saveEvaluation(UUID deliveryId, UUID evaluatorId,
                                                    UUID evaluatedId, String type,
                                                    int rating, String comment) {
        EvaluationEntity eval = EvaluationEntity.builder()
            .id(UUID.randomUUID())
            .deliveryId(deliveryId)
            .evaluatorActorId(evaluatorId)
            .evaluatedActorId(evaluatedId)
            .evaluationType(type)
            .rating(rating)
            .comment(comment)
            .createdAt(Instant.now())
            .build();
        return evaluationRepository.save(eval)
            .doOnSuccess(e -> log.info("[EVALUATION] {} → evaluatorId={} evaluatedId={} rating={}",
                type, evaluatorId, evaluatedId, rating));
    }
}
