package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.EvaluationDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EvaluationUseCase {
    /**
     * The evaluator is the caller ({@code ctx}, lot L3 C5): the {@code evaluatorId} of the body is only an
     * optional profile id of the caller, or, for staff, the party evaluating on whose behalf staff acts.
     */
    Mono<Evaluation> submitEvaluation(EvaluationDTO dto,
                                      com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext ctx);
    Flux<Evaluation> getEvaluationsForPerson(UUID personId);
}
