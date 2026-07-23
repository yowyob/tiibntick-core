package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryNeedEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IDeliveryNeedUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "GOFP — Besoins de livraison", description = "API métier générique — Besoins clients")
@RestController
@RequestMapping("/api/v1/gofp/delivery-needs")
@RequiredArgsConstructor
public class DeliveryNeedCoreController {

    private final IDeliveryNeedUseCase deliveryNeedUseCase;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryNeedEntity> create(@RequestBody DeliveryNeedEntity need) {
        return deliveryNeedUseCase.createDeliveryNeed(need);
    }

    @GetMapping("/{id}")
    public Mono<DeliveryNeedEntity> findById(@PathVariable UUID id) { return deliveryNeedUseCase.findById(id); }

    @GetMapping("/client/{clientActorId}")
    public Flux<DeliveryNeedEntity> findByClient(@PathVariable UUID clientActorId) { return deliveryNeedUseCase.findByClientActorId(clientActorId); }

    @GetMapping("/open")
    public Flux<DeliveryNeedEntity> findOpen() { return deliveryNeedUseCase.findOpen(); }

    @Operation(summary = "Assigner une livraison à un besoin")
    @PatchMapping("/{id}/assign/{deliveryId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryNeedEntity> assign(@PathVariable UUID id, @PathVariable UUID deliveryId) {
        return deliveryNeedUseCase.assignDelivery(id, deliveryId);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryNeedEntity> cancel(@PathVariable UUID id) { return deliveryNeedUseCase.cancel(id); }
}
