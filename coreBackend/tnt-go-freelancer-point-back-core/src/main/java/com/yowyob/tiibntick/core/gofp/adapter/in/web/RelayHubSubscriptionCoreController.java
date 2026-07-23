package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubSubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IRelayHubSubscriptionUseCase;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.RelayHubSubscriptionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Contrôleur REST — Abonnements des points relais.
 *
 * Base path : /api/v1/gofp/subscriptions/relay-hub
 *
 * Endpoints :
 *   POST   /relay-hub/{relayHubId}                   → souscrire / mettre à niveau
 *   GET    /relay-hub/{relayHubId}                   → état de l'abonnement
 *   PATCH  /relay-hub/{relayHubId}/cancel            → annuler
 *   GET    /relay-hub/{relayHubId}/can-accept        → peut-il accepter un colis ?
 *   PATCH  /relay-hub/{relayHubId}/increment-packets → +1 colis en stock
 *   PATCH  /relay-hub/{relayHubId}/decrement-packets → -1 colis en stock
 *
 * @author TiiBnTickTeam
 * @date 10/07/2026
 */
@Tag(name = "GOFP — Abonnements Points Relais",
     description = "API métier — Plans d'abonnement opérateurs de points relais")
@RestController
@RequestMapping("/api/v1/gofp/subscriptions/relay-hub")
@RequiredArgsConstructor
public class RelayHubSubscriptionCoreController {

    private final IRelayHubSubscriptionUseCase subscriptionUseCase;

    @Operation(summary = "Souscrire ou changer de plan pour un point relais")
    @PostMapping("/{relayHubId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RelayHubSubscriptionEntity> subscribe(
            @PathVariable UUID relayHubId,
            @RequestParam String subscriptionType,
            @RequestParam(required = false) String paymentMethod) {
        return subscriptionUseCase.subscribe(
                relayHubId,
                RelayHubSubscriptionType.fromValue(subscriptionType),
                paymentMethod);
    }

    @Operation(summary = "Obtenir l'abonnement actuel d'un point relais")
    @GetMapping("/{relayHubId}")
    public Mono<RelayHubSubscriptionEntity> find(@PathVariable UUID relayHubId) {
        return subscriptionUseCase.findByRelayHubId(relayHubId);
    }

    @Operation(summary = "Annuler l'abonnement d'un point relais")
    @PatchMapping("/{relayHubId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Mono<RelayHubSubscriptionEntity> cancel(@PathVariable UUID relayHubId) {
        return subscriptionUseCase.cancel(relayHubId);
    }

    @Operation(summary = "Vérifier si le point relais peut accepter un nouveau colis")
    @GetMapping("/{relayHubId}/can-accept")
    public Mono<Boolean> canAccept(@PathVariable UUID relayHubId) {
        return subscriptionUseCase.canAcceptPacket(relayHubId);
    }

    @Operation(summary = "Incrémenter le compteur de colis (appelé à la création d'un dépôt)")
    @PatchMapping("/{relayHubId}/increment-packets")
    @PreAuthorize("isAuthenticated()")
    public Mono<RelayHubSubscriptionEntity> incrementPackets(@PathVariable UUID relayHubId) {
        return subscriptionUseCase.incrementPacketsUsed(relayHubId);
    }

    @Operation(summary = "Décrémenter le compteur de colis (appelé à la récupération d'un colis)")
    @PatchMapping("/{relayHubId}/decrement-packets")
    @PreAuthorize("isAuthenticated()")
    public Mono<RelayHubSubscriptionEntity> decrementPackets(@PathVariable UUID relayHubId) {
        return subscriptionUseCase.decrementPacketsUsed(relayHubId);
    }
}
