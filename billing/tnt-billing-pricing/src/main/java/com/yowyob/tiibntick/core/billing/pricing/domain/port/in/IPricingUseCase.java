package com.yowyob.tiibntick.core.billing.pricing.domain.port.in;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IPricingUseCase {

    Mono<PriceEvaluation> evaluatePolicy(UUID policyId, PricingContext context);

    Mono<PriceEvaluation> evaluateDefaultPolicy(UUID tenantId, PricingContext context);

    Mono<Money> computeSellingPrice(UUID policyId, PricingContext context);

    Mono<PriceEvaluation> simulatePrice(UUID policyId, PricingContext context);
}
