package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonPricingEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.LogisticsPricingEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IPricingUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryPersonPricingRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.ILogisticsPricingRepository;
import com.yowyob.tiibntick.core.gofp.domain.model.PricingCalculation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingCoreService implements IPricingUseCase {

    private final IDeliveryPersonPricingRepository dpPricingRepository;
    private final ILogisticsPricingRepository      logisticsPricingRepository;

    @Override
    public Mono<PricingCalculation> calculateDeliveryPrice(UUID freelancerActorId,
                                                            double distanceKm,
                                                            double weightKg,
                                                            double volumeCbm,
                                                            boolean fragile,
                                                            boolean perishable) {
        return dpPricingRepository.findByFreelancerActorId(freelancerActorId)
            .defaultIfEmpty(defaultDeliveryPricing(freelancerActorId))
            .map(p -> PricingCalculation.compute(
                p.getBaseFee(),
                p.getPricePerKm(),   distanceKm,
                p.getPricePerKg(),   weightKg,
                p.getPricePerCbm(),  volumeCbm,
                fragile    ? p.getFragileSurcharge()    : 0.0,
                perishable ? p.getPerishableSurcharge() : 0.0,
                p.getCurrency() != null ? p.getCurrency() : "FCFA"
            ));
    }

    @Override
    public Mono<PricingCalculation> calculateStorageFee(UUID relayHubId,
                                                         double weightKg,
                                                         double volumeCbm,
                                                         int daysStored,
                                                         boolean fragile,
                                                         boolean perishable) {
        return logisticsPricingRepository.findByRelayHubId(relayHubId)
            .defaultIfEmpty(defaultLogisticsPricing(relayHubId))
            .map(p -> {
                int billableDays = Math.max(0, daysStored - p.getGracePeriodDays());
                double storageFee = p.getPricePerDay() * billableDays;
                double penalty    = billableDays > p.getGracePeriodDays()
                    ? p.getPenaltyPerDay() * (billableDays - p.getGracePeriodDays())
                    : 0.0;
                return PricingCalculation.compute(
                    p.getBaseFee() + storageFee + penalty,
                    0, 0,  // pas de charge au km pour le stockage
                    p.getPricePerKg(),  weightKg,
                    p.getPricePerCbm(), volumeCbm,
                    fragile    ? p.getFragileSurcharge()    : 0.0,
                    perishable ? p.getPerishableSurcharge() : 0.0,
                    p.getCurrency() != null ? p.getCurrency() : "FCFA"
                );
            });
    }

    @Override
    public Mono<DeliveryPersonPricingEntity> getFreelancerPricing(UUID freelancerActorId) {
        return dpPricingRepository.findByFreelancerActorId(freelancerActorId)
            .switchIfEmpty(Mono.defer(() -> dpPricingRepository.save(defaultDeliveryPricing(freelancerActorId))));
    }

    @Override
    public Mono<DeliveryPersonPricingEntity> updateFreelancerPricing(UUID freelancerActorId,
                                                                       DeliveryPersonPricingEntity pricing) {
        return dpPricingRepository.findByFreelancerActorId(freelancerActorId)
            .defaultIfEmpty(defaultDeliveryPricing(freelancerActorId))
            .flatMap(existing -> {
                pricing.setId(existing.getId());
                pricing.setFreelancerActorId(freelancerActorId);
                pricing.setUpdatedAt(Instant.now());
                if (pricing.getCreatedAt() == null) pricing.setCreatedAt(existing.getCreatedAt());
                return dpPricingRepository.save(pricing);
            });
    }

    @Override
    public Mono<LogisticsPricingEntity> getLogisticsPricing(UUID relayHubId) {
        return logisticsPricingRepository.findByRelayHubId(relayHubId)
            .switchIfEmpty(Mono.defer(() -> logisticsPricingRepository.save(defaultLogisticsPricing(relayHubId))));
    }

    @Override
    public Mono<LogisticsPricingEntity> updateLogisticsPricing(UUID relayHubId,
                                                                 LogisticsPricingEntity pricing) {
        return logisticsPricingRepository.findByRelayHubId(relayHubId)
            .defaultIfEmpty(defaultLogisticsPricing(relayHubId))
            .flatMap(existing -> {
                pricing.setId(existing.getId());
                pricing.setRelayHubId(relayHubId);
                pricing.setUpdatedAt(Instant.now());
                if (pricing.getCreatedAt() == null) pricing.setCreatedAt(existing.getCreatedAt());
                return logisticsPricingRepository.save(pricing);
            });
    }

    // ── defaults ─────────────────────────────────────────────────────────────

    private DeliveryPersonPricingEntity defaultDeliveryPricing(UUID freelancerActorId) {
        return DeliveryPersonPricingEntity.builder()
            .freelancerActorId(freelancerActorId)
            .baseFee(0.0).pricePerKm(0.0).pricePerKg(0.0).pricePerCbm(0.0)
            .fragileSurcharge(0.0).perishableSurcharge(0.0)
            .currency("FCFA").createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
    }

    private LogisticsPricingEntity defaultLogisticsPricing(UUID relayHubId) {
        return LogisticsPricingEntity.builder()
            .relayHubId(relayHubId)
            .baseFee(0.0).pricePerKg(0.0).pricePerCbm(0.0)
            .pricePerDay(0.0).gracePeriodDays(0).penaltyPerDay(0.0)
            .fragileSurcharge(0.0).perishableSurcharge(0.0)
            .currency("FCFA").createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
    }
}
