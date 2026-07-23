package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayDepositEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IRelayDepositUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "GOFP — Dépôts Relais", description = "API métier générique — Dépôts en points relais")
@RestController
@RequestMapping("/api/v1/gofp/relay-deposits")
@RequiredArgsConstructor
public class RelayDepositCoreController {

    private final IRelayDepositUseCase relayDepositUseCase;

    @Operation(summary = "Créer un dépôt en point relais")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RelayDepositEntity> create(@RequestParam UUID packetId,
                                            @RequestParam UUID clientActorId,
                                            @RequestParam UUID relayHubId,
                                            @RequestParam(required = false) UUID freelancerActorId,
                                            @RequestParam(required = false) UUID deliveryId) {
        return relayDepositUseCase.createDeposit(packetId, clientActorId, relayHubId, freelancerActorId, deliveryId);
    }

    @GetMapping("/{id}")
    public Mono<RelayDepositEntity> findById(@PathVariable UUID id) { return relayDepositUseCase.findById(id); }

    @GetMapping("/hub/{relayHubId}")
    public Flux<RelayDepositEntity> findByHub(@PathVariable UUID relayHubId) { return relayDepositUseCase.findByRelayHubId(relayHubId); }

    @GetMapping("/client/{clientActorId}")
    public Flux<RelayDepositEntity> findByClient(@PathVariable UUID clientActorId) { return relayDepositUseCase.findByClientActorId(clientActorId); }

    @PatchMapping("/{id}/retrieve")
    @PreAuthorize("isAuthenticated()")
    public Mono<RelayDepositEntity> markRetrieved(@PathVariable UUID id) { return relayDepositUseCase.markRetrieved(id); }

    @GetMapping("/{id}/penalty")
    public Mono<Double> penalty(@PathVariable UUID id) { return relayDepositUseCase.calculatePenalty(id); }
}
