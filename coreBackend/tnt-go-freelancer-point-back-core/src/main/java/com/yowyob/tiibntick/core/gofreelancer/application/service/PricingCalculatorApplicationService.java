package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PriceCalculationRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.PriceCalculationResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.PricingCalculatorUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayPointPricingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
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
    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final FreelancerPriceQuoteUseCase freelancerPriceQuoteUseCase;

    /**
     * Priced by the billing engine from the freelancer's active billing policy (or, failing that,
     * their pre-migration grid, or the platform default) — the very quote candidate matching shows.
     * {@code freelancerId} is the delivery-person id; the freelancer's Kernel user id is also
     * searched so that a policy applied from a billing template is honoured.
     */
    @Override
    public Mono<PriceCalculationResponse> calculateFreelancerPrice(UUID freelancerId, PriceCalculationRequest request) {
        return ownerIds(freelancerId)
                .flatMap(ids -> freelancerPriceQuoteUseCase.quote(ids, toInput(request)))
                .map(q -> PriceCalculationResponse.builder()
                        .totalPrice(q.totalPrice())
                        .currency(q.currency())
                        .breakdown(q.breakdown())
                        .build());
    }

    private Mono<List<UUID>> ownerIds(UUID freelancerId) {
        return gofpFreelancerRepository.findByCoreFreelancerId(freelancerId)
                .switchIfEmpty(Mono.defer(() -> gofpFreelancerRepository.findById(freelancerId)))
                .map(f -> {
                    List<UUID> ids = new ArrayList<>(List.of(freelancerId));
                    if (f.getCoreFreelancerId() != null) ids.add(f.getCoreFreelancerId());
                    if (f.getCoreUserId() != null) ids.add(f.getCoreUserId());
                    return ids;
                })
                .onErrorResume(e -> {
                    log.warn("Freelancer {} identity lookup failed — pricing under the given id only", freelancerId, e);
                    return Mono.empty();
                })
                .defaultIfEmpty(List.of(freelancerId));
    }

    private static FreelancerPriceQuoteUseCase.QuoteInput toInput(PriceCalculationRequest r) {
        return new FreelancerPriceQuoteUseCase.QuoteInput(
                r.getDistanceKm() != null ? r.getDistanceKm() : 0,
                r.getWeight() != null ? r.getWeight() : 0,
                r.getVolumeCbm() != null ? r.getVolumeCbm() : 0,
                Boolean.TRUE.equals(r.getIsFragile()),
                Boolean.TRUE.equals(r.getIsPerishable()),
                r.getWaitingMinutes(), r.getPriority(), r.getScheduledAt(),
                r.getPaymentMethod(), r.getDeclaredValueXaf());
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
