package com.yowyob.tiibntick.core.billing.pricing.domain.port.in;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IPricingUseCase {

    Mono<PriceEvaluation> evaluatePolicy(UUID policyId, PricingContext context);

    Mono<PriceEvaluation> evaluateDefaultPolicy(UUID tenantId, PricingContext context);

    Mono<Money> computeSellingPrice(UUID policyId, PricingContext context);

    Mono<PriceEvaluation> simulatePrice(UUID policyId, PricingContext context);

    /**
     * Prices a context against a policy supplied by the caller, persisted or not, and whatever
     * its lifecycle status. Used to preview a template or a freelancer grid before it is saved,
     * so that what is shown is computed by the exact engine that will bill the delivery.
     */
    Mono<PriceEvaluation> evaluateWith(BillingPolicy policy, PricingContext context);
}
