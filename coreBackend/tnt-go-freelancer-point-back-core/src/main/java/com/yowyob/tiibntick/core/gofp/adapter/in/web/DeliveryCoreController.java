package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IDeliveryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "GOFP — Livraisons", description = "API métier générique — Cycle de vie livraison")
@RestController
@RequestMapping("/api/v1/gofp/deliveries")
@RequiredArgsConstructor
public class DeliveryCoreController {

    private final IDeliveryUseCase deliveryUseCase;

    @Operation(summary = "Créer une livraison depuis une annonce assignée")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryEntity> create(@RequestParam UUID announcementId,
                                        @RequestParam UUID freelancerActorId) {
        return deliveryUseCase.createDelivery(announcementId, freelancerActorId);
    }

    @GetMapping("/{id}")
    public Mono<DeliveryEntity> findById(@PathVariable UUID id) {
        return deliveryUseCase.findById(id);
    }

    @GetMapping("/freelancer/{freelancerActorId}")
    public Flux<DeliveryEntity> findByFreelancer(@PathVariable UUID freelancerActorId) {
        return deliveryUseCase.findByFreelancerActorId(freelancerActorId);
    }

    @Operation(summary = "Confirmer le pickup → PICKED_UP")
    @PatchMapping("/{id}/pickup")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryEntity> confirmPickup(@PathVariable UUID id) {
        return deliveryUseCase.confirmPickup(id);
    }

    @Operation(summary = "Démarrer le transit → IN_TRANSIT")
    @PatchMapping("/{id}/transit")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryEntity> startTransit(@PathVariable UUID id) {
        return deliveryUseCase.startTransit(id);
    }

    @Operation(summary = "Déposer au point relais → AT_RELAY")
    @PatchMapping("/{id}/relay/{relayHubId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryEntity> depositAtRelay(@PathVariable UUID id, @PathVariable UUID relayHubId) {
        return deliveryUseCase.depositAtRelay(id, relayHubId);
    }

    @Operation(summary = "Reprendre depuis le point relais → IN_TRANSIT")
    @PatchMapping("/{id}/resume")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryEntity> resumeFromRelay(@PathVariable UUID id) {
        return deliveryUseCase.resumeFromRelay(id);
    }

    @Operation(summary = "Livraison complétée → DELIVERED")
    @PatchMapping("/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryEntity> complete(@PathVariable UUID id,
                                          @RequestParam(required = false) Double lat,
                                          @RequestParam(required = false) Double lon) {
        return deliveryUseCase.completeDelivery(id, lat, lon);
    }

    @Operation(summary = "Livraison échouée → FAILED")
    @PatchMapping("/{id}/fail")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryEntity> fail(@PathVariable UUID id, @RequestParam String reason) {
        return deliveryUseCase.failDelivery(id, reason);
    }

    @Operation(summary = "Annuler une livraison → CANCELLED")
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryEntity> cancel(@PathVariable UUID id) {
        return deliveryUseCase.cancelDelivery(id);
    }

    @Operation(summary = "Mettre à jour la position GPS du livreur")
    @PatchMapping("/{id}/location")
    @PreAuthorize("isAuthenticated()")
    public Mono<Void> updateLocation(@PathVariable UUID id,
                                      @RequestParam double lat,
                                      @RequestParam double lon) {
        return deliveryUseCase.updateLocation(id, lat, lon);
    }
}
