package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PriceCalculationRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.PriceCalculationResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.PricingCalculatorUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FreelancerPricingRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayPointPricingRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerPricingPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing PricingCalculatorUseCase.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingCalculatorApplicationService implements PricingCalculatorUseCase {

    private final RelayPointPricingRepository relayPointPricingRepository;
    private final FreelancerPricingRepository freelancerPricingRepository;

    @Override
    public Mono<PriceCalculationResponse> calculateFreelancerPrice(UUID freelancerId, PriceCalculationRequest request) {
        return freelancerPricingRepository.findByDeliveryPersonId(freelancerId)
                .map(policy -> computeFromPolicy(policy, request))
                .defaultIfEmpty(computeDefaults(request));
    }

    private PriceCalculationResponse computeFromPolicy(FreelancerPricingPolicy policy, PriceCalculationRequest request) {
        double base = policy.getBaseFee() != null ? policy.getBaseFee() : 0;
        double weightCost = (request.getWeight() != null ? request.getWeight() : 0)
                * (policy.getPricePerKg() != null ? policy.getPricePerKg() : 0);
        double distanceCost = (request.getDistanceKm() != null ? request.getDistanceKm() : 0)
                * (policy.getPricePerKm() != null ? policy.getPricePerKm() : 0);
        double volumeCost = 0;
        if (request.getVolumeCbm() != null && policy.getPricePerCbm() != null) {
            volumeCost = request.getVolumeCbm() * policy.getPricePerCbm();
        }
        double fragileSurcharge = Boolean.TRUE.equals(request.getIsFragile())
                && policy.getFragileSurcharge() != null ? policy.getFragileSurcharge() : 0;
        double perishableSurcharge = Boolean.TRUE.equals(request.getIsPerishable())
                && policy.getPerishableSurcharge() != null ? policy.getPerishableSurcharge() : 0;
        double total = base + weightCost + distanceCost + volumeCost + fragileSurcharge + perishableSurcharge;
        return PriceCalculationResponse.builder()
                .totalPrice(total)
                .currency(policy.getCurrency() != null ? policy.getCurrency() : "XAF")
                .breakdown(String.format(
                        "Base: %.0f + Poids: %.0f + Distance: %.0f + Volume: %.0f + Surcharges: %.0f",
                        base, weightCost, distanceCost, volumeCost, fragileSurcharge + perishableSurcharge))
                .build();
    }

    private PriceCalculationResponse computeDefaults(PriceCalculationRequest request) {
        double base = 500.0;
        double weightCost = (request.getWeight() != null ? request.getWeight() : 0) * 100.0;
        double distanceCost = (request.getDistanceKm() != null ? request.getDistanceKm() : 0) * 50.0;
        double fragileSurcharge = Boolean.TRUE.equals(request.getIsFragile()) ? 200.0 : 0;
        double perishableSurcharge = Boolean.TRUE.equals(request.getIsPerishable()) ? 300.0 : 0;
        double total = base + weightCost + distanceCost + fragileSurcharge + perishableSurcharge;
        return PriceCalculationResponse.builder()
                .totalPrice(total)
                .currency("XAF")
                .breakdown(String.format("Base: %.0f + Poids: %.0f + Distance: %.0f + Surcharges: %.0f (defaults)",
                        base, weightCost, distanceCost, fragileSurcharge + perishableSurcharge))
                .build();
    }

    @Override
    public Mono<PriceCalculationResponse> calculateLogisticsPrice(UUID relayPointId, PriceCalculationRequest request) {
        return relayPointPricingRepository.findByLogisticsId(relayPointId)
                .map(policy -> {
                    int days = request.getDays() != null ? request.getDays() : 1;
                    double base = policy.getBaseFee() != null ? policy.getBaseFee() : 0;
                    double perDay = policy.getPricePerDay() != null ? policy.getPricePerDay() * days : 0;
                    double weightCost = (request.getWeight() != null ? request.getWeight() : 0)
                            * (policy.getPricePerKg() != null ? policy.getPricePerKg() : 0);
                    double fragileSurcharge = Boolean.TRUE.equals(request.getIsFragile())
                            && policy.getFragileSurcharge() != null ? policy.getFragileSurcharge() : 0;
                    double total = base + perDay + weightCost + fragileSurcharge;
                    return PriceCalculationResponse.builder()
                            .totalPrice(total)
                            .currency(policy.getCurrency() != null ? policy.getCurrency() : "XAF")
                            .breakdown(String.format("Base: %.0f + Stockage(%dj): %.0f + Poids: %.0f + Fragile: %.0f",
                                    base, days, perDay, weightCost, fragileSurcharge))
                            .build();
                })
                .switchIfEmpty(Mono.just(PriceCalculationResponse.builder()
                        .totalPrice(0.0)
                        .currency("XAF")
                        .breakdown("Aucune grille tarifaire définie pour ce point relais")
                        .build()));
    }
}
