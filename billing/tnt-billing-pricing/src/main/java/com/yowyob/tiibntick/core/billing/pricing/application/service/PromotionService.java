package com.yowyob.tiibntick.core.billing.pricing.application.service;

import com.yowyob.tiibntick.core.billing.pricing.domain.exception.BillingPolicyNotFoundException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.Promotion;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final IBillingPolicyRepository policyRepository;
    private final DslCompilerService dslCompilerService;
    private final DslEvaluator dslEvaluator;

    public Mono<Optional<Money>> applyPromoCode(UUID policyId, String promoCode,
                                                 Money currentPrice, PricingContext ctx) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .map(policy -> findAndApplyPromo(policy, promoCode, currentPrice, ctx));
    }

    public Mono<Boolean> validatePromoCode(UUID policyId, String promoCode) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .map(policy -> isPromoValid(policy, promoCode));
    }

    private Optional<Money> findAndApplyPromo(BillingPolicy policy, String code,
                                               Money price, PricingContext ctx) {
        if (policy.getPromotions() == null) return Optional.empty();

        return policy.getPromotions().stream()
                .filter(p -> code.equalsIgnoreCase(p.getCode()))
                .filter(p -> p.isActive(LocalDateTime.now()))
                .filter(Promotion::hasRemainingUsages)
                .filter(p -> p.meetsMinimumAmount(price))
                .filter(p -> conditionMet(p, ctx))
                .findFirst()
                .map(p -> p.computeDiscount(price));
    }

    private boolean isPromoValid(BillingPolicy policy, String code) {
        if (policy.getPromotions() == null) return false;
        return policy.getPromotions().stream()
                .anyMatch(p -> code.equalsIgnoreCase(p.getCode())
                        && p.isActive(LocalDateTime.now())
                        && p.hasRemainingUsages());
    }

    private boolean conditionMet(Promotion promo, PricingContext ctx) {
        if (promo.getConditionExpression() == null || promo.getConditionExpression().isBlank()) return true;
        try {
            AstNode ast = dslCompilerService.compileCondition(promo.getConditionExpression());
            return dslEvaluator.evaluate(ast, ctx);
        } catch (Exception e) {
            log.warn("Promo condition evaluation failed: {}", e.getMessage());
            return false;
        }
    }
}
