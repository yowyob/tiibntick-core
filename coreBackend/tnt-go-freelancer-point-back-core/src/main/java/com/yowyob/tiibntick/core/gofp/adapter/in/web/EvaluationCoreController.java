package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.EvaluationEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IEvaluationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Tag(name = "GOFP — Évaluations", description = "API métier générique — Notations post-livraison")
@RestController
@RequestMapping("/api/v1/gofp/evaluations")
@RequiredArgsConstructor
public class EvaluationCoreController {

    private final IEvaluationUseCase evaluationUseCase;

    @Operation(summary = "Client note le livreur")
    @PostMapping("/client-rates-freelancer")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EvaluationEntity> clientRatesFreelancer(
            @RequestParam UUID deliveryId,
            @RequestParam UUID clientActorId,
            @RequestParam UUID freelancerActorId,
            @RequestParam int rating,
            @RequestParam(required = false) String comment) {
        return evaluationUseCase.clientRatesFreelancer(deliveryId, clientActorId, freelancerActorId, rating, comment);
    }

    @Operation(summary = "Livreur note le client")
    @PostMapping("/freelancer-rates-client")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EvaluationEntity> freelancerRatesClient(
            @RequestParam UUID deliveryId,
            @RequestParam UUID freelancerActorId,
            @RequestParam UUID clientActorId,
            @RequestParam int rating,
            @RequestParam(required = false) String comment) {
        return evaluationUseCase.freelancerRatesClient(deliveryId, freelancerActorId, clientActorId, rating, comment);
    }

    @GetMapping("/delivery/{deliveryId}")
    public Flux<EvaluationEntity> findByDelivery(@PathVariable UUID deliveryId) { return evaluationUseCase.findByDeliveryId(deliveryId); }

    @GetMapping("/actor/{actorId}")
    public Flux<EvaluationEntity> findByActor(@PathVariable UUID actorId) { return evaluationUseCase.findByEvaluatedActorId(actorId); }
}
