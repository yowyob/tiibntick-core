package com.yowyob.tiibntick.core.billing.pricing.application.service;

import com.yowyob.tiibntick.core.billing.pricing.domain.exception.BillingPolicyNotFoundException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.LoyaltyRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoyaltyDiscountService {

    private final IBillingPolicyRepository policyRepository;

    public Mono<BigDecimal> getEligibleDiscountPct(UUID policyId, PricingContext ctx) {
        // Audit n°7 · #5 (IDOR) — scope the policy lookup to the caller's tenant, carried
        // on the PricingContext, so a caller cannot infer another tenant's loyalty rules.
        return policyRepository.findByIdAndTenantId(policyId, ctx.getTenantId())
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .map(policy -> {
                    List<LoyaltyRule> rules = policy.getLoyaltyRules();
                    if (rules == null || rules.isEmpty()) return BigDecimal.ZERO;
                    return rules.stream()
                            .filter(r -> r.isEligible(ctx))
                            .map(LoyaltyRule::getDiscountPercentage)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                });
    }

    public Mono<Money> computeDiscount(UUID policyId, Money currentPrice, PricingContext ctx) {
        return policyRepository.findByIdAndTenantId(policyId, ctx.getTenantId())
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .map(policy -> {
                    List<LoyaltyRule> rules = policy.getLoyaltyRules();
                    if (rules == null || rules.isEmpty()) return Money.zeroXAF();
                    return rules.stream()
                            .filter(r -> r.isEligible(ctx))
                            .map(r -> r.computeDiscount(currentPrice))
                            .reduce(Money.zeroXAF(), Money::add);
                });
    }
}
