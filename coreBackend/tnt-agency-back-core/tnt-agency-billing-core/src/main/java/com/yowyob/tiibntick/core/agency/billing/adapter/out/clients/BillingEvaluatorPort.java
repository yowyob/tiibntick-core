package com.yowyob.tiibntick.core.agency.billing.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface BillingEvaluatorPort {

    Mono<BillingResult> evaluate(BillingEvaluationRequest request);

    record BillingEvaluationRequest(
            UUID tenantId,
            UUID agencyId,
            UUID policyId,
            double distanceKm,
            double weightKg,
            double volumeCbm,
            String promoCode
    ) {}

    record BillingResult(
            BigDecimal amount,
            BigDecimal basePrice,
            BigDecimal distanceSurcharge,
            BigDecimal weightSurcharge,
            BigDecimal discount,
            String currency
    ) {
        public static BillingResult zero(String currency) {
            return new BillingResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, currency
            );
        }
    }
}
