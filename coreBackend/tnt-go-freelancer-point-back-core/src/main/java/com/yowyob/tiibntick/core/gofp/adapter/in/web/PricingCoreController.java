package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonPricingEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.LogisticsPricingEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IPricingUseCase;
import com.yowyob.tiibntick.core.gofp.domain.model.PricingCalculation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "GOFP — Pricing", description = "API métier générique — Calcul tarifaire")
@RestController
@RequestMapping("/api/v1/gofp/pricing")
@RequiredArgsConstructor
public class PricingCoreController {

    private final IPricingUseCase pricingUseCase;

    @Operation(summary = "Calculer le prix d'une livraison pour un livreur")
    @GetMapping("/delivery/calculate")
    public Mono<PricingCalculation> calculateDelivery(
            @RequestParam UUID freelancerActorId,
            @RequestParam double distanceKm,
            @RequestParam(defaultValue = "0") double weightKg,
            @RequestParam(defaultValue = "0") double volumeCbm,
            @RequestParam(defaultValue = "false") boolean fragile,
            @RequestParam(defaultValue = "false") boolean perishable) {
        return pricingUseCase.calculateDeliveryPrice(freelancerActorId, distanceKm, weightKg, volumeCbm, fragile, perishable);
    }

    @Operation(summary = "Calculer les frais de stockage d'un point relais")
    @GetMapping("/storage/calculate")
    public Mono<PricingCalculation> calculateStorage(
            @RequestParam UUID relayHubId,
            @RequestParam(defaultValue = "0") double weightKg,
            @RequestParam(defaultValue = "0") double volumeCbm,
            @RequestParam(defaultValue = "0") int daysStored,
            @RequestParam(defaultValue = "false") boolean fragile,
            @RequestParam(defaultValue = "false") boolean perishable) {
        return pricingUseCase.calculateStorageFee(relayHubId, weightKg, volumeCbm, daysStored, fragile, perishable);
    }

    @GetMapping("/freelancer/{freelancerActorId}")
    public Mono<DeliveryPersonPricingEntity> getFreelancerPricing(@PathVariable UUID freelancerActorId) {
        return pricingUseCase.getFreelancerPricing(freelancerActorId);
    }

    @PutMapping("/freelancer/{freelancerActorId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<DeliveryPersonPricingEntity> updateFreelancerPricing(@PathVariable UUID freelancerActorId,
                                                                       @RequestBody DeliveryPersonPricingEntity pricing) {
        return pricingUseCase.updateFreelancerPricing(freelancerActorId, pricing);
    }

    @GetMapping("/logistics/{relayHubId}")
    public Mono<LogisticsPricingEntity> getLogisticsPricing(@PathVariable UUID relayHubId) {
        return pricingUseCase.getLogisticsPricing(relayHubId);
    }

    @PutMapping("/logistics/{relayHubId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<LogisticsPricingEntity> updateLogisticsPricing(@PathVariable UUID relayHubId,
                                                                 @RequestBody LogisticsPricingEntity pricing) {
        return pricingUseCase.updateLogisticsPricing(relayHubId, pricing);
    }
}
