package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.EvaluationUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.EvaluationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationUseCase evaluationUseCase;

    @PostMapping
    public Mono<ResponseEntity<Evaluation>> submitEvaluation(@RequestBody EvaluationDTO dto) {
        return evaluationUseCase.submitEvaluation(dto)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/person/{personId}")
    public Flux<Evaluation> getEvaluationsForPerson(@PathVariable UUID personId) {
        return evaluationUseCase.getEvaluationsForPerson(personId);
    }
}
