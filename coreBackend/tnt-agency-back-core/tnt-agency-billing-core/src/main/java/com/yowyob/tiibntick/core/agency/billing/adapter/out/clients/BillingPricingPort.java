package com.yowyob.tiibntick.core.agency.billing.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface BillingPricingPort {

    record PricingRequest(
            UUID tenantId,
            UUID agencyId,
            UUID policyId,
            UUID missionId,
            double distanceKm,
            double weightKg
    ) {}

    record PricingResult(BigDecimal amount, String currency) {}

    Mono<PricingResult> evaluate(PricingRequest request);
}
