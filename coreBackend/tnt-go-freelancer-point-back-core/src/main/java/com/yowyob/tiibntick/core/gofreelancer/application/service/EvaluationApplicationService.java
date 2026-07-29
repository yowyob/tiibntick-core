package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.EvaluationDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.EvaluationRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.SentimentAnalysisUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.EvaluationUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpClientRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpRelayPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Application service implementing EvaluationUseCase.
 * Handles delivery and relay point evaluation submissions, sentiment analysis,
 * and average rating updates across actors.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationApplicationService implements EvaluationUseCase {

    private final EvaluationRepository evaluationRepository;
    private final SentimentAnalysisUseCase sentimentAnalysisUseCase;
    private final GofpRelayPointRepository gofpRelayPointRepository;
    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final GofpClientRepository gofpClientRepository;

    @Override
    public Mono<Evaluation> submitEvaluation(EvaluationDTO dto) {
        Mono<Integer> ratingMono;
        if (dto.getComment() != null && !dto.getComment().trim().isEmpty()) {
            ratingMono = sentimentAnalysisUseCase.predictRating(dto.getComment())
                    .defaultIfEmpty(dto.getRating() != null ? dto.getRating() : 5);
        } else {
            ratingMono = Mono.just(dto.getRating() != null ? dto.getRating() : 5);
        }

        return ratingMono.flatMap(finalRating -> {
            if (finalRating < 1 || finalRating > 5) {
                return Mono.error(new IllegalArgumentException("La note doit être comprise entre 1 et 5."));
            }

            Evaluation evaluation = Evaluation.builder()
                    .id(UUID.randomUUID())
                    .evaluatedId(dto.getEvaluatedId())
                    .evaluatorId(dto.getEvaluatorId())
                    .deliveryId(dto.getDeliveryId())
                    .rating(finalRating)
                    .comment(dto.getComment())
                    .type(dto.getType())
                    .createdAt(LocalDateTime.now())
                    .build();

            return evaluationRepository.save(evaluation)
                    .flatMap(saved -> updateGlobalRating(saved.getEvaluatedId()).thenReturn(saved))
                    .doOnSuccess(saved -> log.info("Evaluation saved successfully for evaluatedId={}, rating={}, type={}",
                            saved.getEvaluatedId(), saved.getRating(), saved.getType()))
                    .doOnError(e -> log.error("Failed to save evaluation for delivery/packet {}", dto.getDeliveryId(), e));
        });
    }

    private Mono<Void> updateGlobalRating(UUID evaluatedId) {
        if (evaluatedId == null) {
            return Mono.empty();
        }

        return evaluationRepository.findByEvaluatedId(evaluatedId)
                .collectList()
                .flatMap(evaluations -> {
                    if (evaluations.isEmpty()) {
                        return Mono.empty();
                    }

                    double sum = 0;
                    for (Evaluation eval : evaluations) {
                        sum += eval.getRating();
                    }
                    double average = Math.round((sum / evaluations.size()) * 10.0) / 10.0;

                    // 1. Try updating Relay Point rating
                    Mono<Void> updateRp = gofpRelayPointRepository.findByCoreRelayPointId(evaluatedId)
                            .switchIfEmpty(gofpRelayPointRepository.findById(evaluatedId))
                            .flatMap(rp -> {
                                rp.setRating(average);
                                rp.setUpdatedAt(Instant.now());
                                return gofpRelayPointRepository.save(rp);
                            }).then();

                    // 2. Try updating Freelancer rating
                    Mono<Void> updateFreelancer = gofpFreelancerRepository.findByCoreFreelancerId(evaluatedId)
                            .switchIfEmpty(gofpFreelancerRepository.findById(evaluatedId))
                            .flatMap(f -> {
                                f.setRating(average);
                                f.setUpdatedAt(Instant.now());
                                return gofpFreelancerRepository.save(f);
                            }).then();

                    // 3. Try updating Client rating
                    Mono<Void> updateClient = gofpClientRepository.findByCoreClientId(evaluatedId)
                            .switchIfEmpty(gofpClientRepository.findById(evaluatedId))
                            .flatMap(c -> {
                                c.setRating(average);
                                c.setUpdatedAt(Instant.now());
                                return gofpClientRepository.save(c);
                            }).then();

                    return Mono.when(updateRp, updateFreelancer, updateClient);
                });
    }

    @Override
    public Flux<Evaluation> getEvaluationsForPerson(UUID personId) {
        return evaluationRepository.findByEvaluatedId(personId);
    }
}
