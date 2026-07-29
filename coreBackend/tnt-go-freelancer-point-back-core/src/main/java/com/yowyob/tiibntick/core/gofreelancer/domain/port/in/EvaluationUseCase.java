package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.EvaluationDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EvaluationUseCase {
    Mono<Evaluation> submitEvaluation(EvaluationDTO dto);
    Flux<Evaluation> getEvaluationsForPerson(UUID personId);
}
