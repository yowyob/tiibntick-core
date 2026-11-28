package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.EvaluationUseCase;
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
    public Mono<ResponseEntity<Evaluation>> submitEvaluation(
            @RequestBody EvaluationDTO dto,
            @com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser(required = false)
            com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext ctx) {
        return evaluationUseCase.submitEvaluation(dto, ctx)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just(ResponseEntity.badRequest().build()))
                // the core keeps one rating per mission and rater
                .onErrorResume(e -> e.getClass() == IllegalStateException.class,
                        e -> Mono.just(ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build()))
                // evaluator other than the caller (C5)
                .onErrorResume(org.springframework.security.access.AccessDeniedException.class,
                        e -> Mono.just(ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build()));
    }

    @GetMapping("/person/{personId}")
    public Flux<Evaluation> getEvaluationsForPerson(@PathVariable UUID personId) {
        return evaluationUseCase.getEvaluationsForPerson(personId);
    }
}
