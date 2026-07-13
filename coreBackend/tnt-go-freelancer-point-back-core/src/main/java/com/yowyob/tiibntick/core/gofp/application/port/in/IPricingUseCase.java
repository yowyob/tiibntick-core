package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonPricingEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.LogisticsPricingEntity;
import com.yowyob.tiibntick.core.gofp.domain.model.PricingCalculation;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IPricingUseCase {

    /** Calcule le prix d'une livraison pour un livreur donné. */
    Mono<PricingCalculation> calculateDeliveryPrice(UUID freelancerActorId,
                                                     double distanceKm,
                                                     double weightKg,
                                                     double volumeCbm,
                                                     boolean fragile,
                                                     boolean perishable);

    /** Calcule les frais de stockage pour un point relais. */
    Mono<PricingCalculation> calculateStorageFee(UUID relayHubId,
                                                  double weightKg,
                                                  double volumeCbm,
                                                  int daysStored,
                                                  boolean fragile,
                                                  boolean perishable);

    // Gestion des politiques tarifaires livreur
    Mono<DeliveryPersonPricingEntity> getFreelancerPricing(UUID freelancerActorId);
    Mono<DeliveryPersonPricingEntity> updateFreelancerPricing(UUID freelancerActorId, DeliveryPersonPricingEntity pricing);

    // Gestion des politiques tarifaires point relais
    Mono<LogisticsPricingEntity> getLogisticsPricing(UUID relayHubId);
    Mono<LogisticsPricingEntity> updateLogisticsPricing(UUID relayHubId, LogisticsPricingEntity pricing);
}
