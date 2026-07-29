package com.yowyob.tiibntick.core.gofreelancer.application.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Porté vers le Layer 6 Core.
 * Délègue au module tnt-billing-pricing via IBillingPort.
 */
@Service
@RequiredArgsConstructor
public class PricingCalculatorUseCase {

    // private final IBillingPort billingPort;

    public Mono<Object> calculateFreelancerPrice(UUID freelancerId, Object request) {
        // TODO: Call IBillingPort to fetch policy and calculate.
        return Mono.empty();
    }

    public Mono<Object> calculateLogisticsPrice(UUID relayPointId, Object request) {
        // TODO: Call IBillingPort to fetch policy and calculate.
        return Mono.empty();
    }
}
