package com.yowyob.tiibntick.core.billing.pricing.application.service;

import com.yowyob.tiibntick.core.billing.pricing.domain.exception.NoPricingRuleMatchException;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.PolicyNotActiveException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.*;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.LineItemType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeStackMode;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IPricingUseCase;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core pricing engine — evaluates a {@link BillingPolicy} against a {@link PricingContext}
 * and produces a {@link PriceEvaluation} with full breakdown.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #evaluateSpecialSurcharges} — CUMULATIVE / EXCLUSIVE_HIGHEST / CAPPED stacking</li>
 *   <li>{@link #computeHubStorageFee} — palier-based hub storage fee</li>
 *   <li>{@link #computeNetworkTransitFee} — per-hop transit fee</li>
 *   <li>Integration of  PricingContext fields (storageHours, networkHopCount)</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingEngineService implements IPricingUseCase {

    private final IBillingPolicyRepository policyRepository;
    private final DslCompilerService dslCompilerService;
    private final DslEvaluator dslEvaluator;

    @Override
    public Mono<PriceEvaluation> evaluatePolicy(UUID policyId, PricingContext ctx) {
        // Audit n°7 · #5 (IDOR) — PricingContext already carries the caller's tenantId
        // (set by the controller from JWT); scope the policy lookup with it so a caller
        // cannot evaluate — and thereby infer the pricing rules of — another tenant's policy.
        return policyRepository.findByIdAndTenantId(policyId, ctx.getTenantId())
                .switchIfEmpty(Mono.error(
                        new com.yowyob.tiibntick.core.billing.pricing.domain.exception
                                .BillingPolicyNotFoundException(policyId)))
                .flatMap(policy -> computeEvaluation(policy, ctx));
    }

    @Override
    public Mono<PriceEvaluation> evaluateDefaultPolicy(UUID tenantId, PricingContext ctx) {
        return policyRepository.findDefaultByTenantId(tenantId)
                .switchIfEmpty(Mono.error(
                        new com.yowyob.tiibntick.core.billing.pricing.domain.exception
                                .BillingPolicyNotFoundException("No default policy for tenant: " + tenantId)))
                .flatMap(policy -> computeEvaluation(policy, ctx));
    }

    @Override
    public Mono<Money> computeSellingPrice(UUID policyId, PricingContext ctx) {
        return evaluatePolicy(policyId, ctx).map(PriceEvaluation::getSellingPrice);
    }

    @Override
    public Mono<PriceEvaluation> simulatePrice(UUID policyId, PricingContext ctx) {
        return evaluatePolicy(policyId, ctx);
    }

    // ── Core evaluation pipeline ─────────────────────────────────────────────

    private Mono<PriceEvaluation> computeEvaluation(BillingPolicy policy, PricingContext ctx) {
        return Mono.fromCallable(() -> {
            if (!policy.isActive()) {
                throw new PolicyNotActiveException(policy.getId());
            }

            String currency = policy.currencyCode();
            List<PriceLineItem> breakdown = new ArrayList<>();
            List<UUID> appliedSurchargeIds = new ArrayList<>();
            List<UUID> appliedPromotionIds = new ArrayList<>();

            // ── Step 1: Base pricing rule ─────────────────────────────────────
            Optional<PricingRule> matchedRuleOpt = findMatchingPricingRule(policy, ctx);
            if (matchedRuleOpt.isEmpty()) {
                throw new NoPricingRuleMatchException(policy.getId());
            }

            PricingRule matchedRule = matchedRuleOpt.get();
            Money current = matchedRule.getBasePrice();
            breakdown.add(PriceLineItem.of("Base price", LineItemType.BASE, current));

            if (matchedRule.hasPerKmRate()) {
                Money kmCharge = matchedRule.getPerKmRate().multiply(ctx.getDistanceKm());
                current = current.add(kmCharge);
                breakdown.add(PriceLineItem.of(
                        String.format("%.1f km × %s/km", ctx.getDistanceKm(), matchedRule.getPerKmRate()),
                        LineItemType.PER_KM, kmCharge));
            }

            if (matchedRule.hasPerKgRate()) {
                Money kgCharge = matchedRule.getPerKgRate().multiply(ctx.getWeightKg());
                current = current.add(kgCharge);
                breakdown.add(PriceLineItem.of(
                        String.format("%.1f kg × %s/kg", ctx.getWeightKg(), matchedRule.getPerKgRate()),
                        LineItemType.PER_KG, kgCharge));
            }

            current = applyMinMax(matchedRule, current);

            // ── Step 2: Standard surcharge rules ─────────────────────────────
            if (policy.getSurchargeRules() != null) {
                for (SurchargeRule sr : policy.getSurchargeRules()) {
                    if (evaluateCondition(sr.getConditionExpression(), ctx)) {
                        Money surcharge = sr.computeSurcharge(current);
                        current = current.add(surcharge);
                        breakdown.add(PriceLineItem.of(sr.getName(), LineItemType.SURCHARGE, surcharge));
                        appliedSurchargeIds.add(sr.getId());
                    }
                }
            }

            // ── Step 3:  — Special surcharge rules ────────────────────────
            List<SpecialSurchargeRule> activeSpecials = policy.activeSpecialSurcharges();
            if (!activeSpecials.isEmpty()) {
                Money specialSurchargeTotal = evaluateSpecialSurcharges(
                        activeSpecials, ctx, current, breakdown);
                current = current.add(specialSurchargeTotal);
            }

            // ── Step 4:  — Hub storage fee ───────────────────────────────
            if (ctx.getStorageHours() != null && ctx.getStorageHours() > 0) {
                PackageType primaryType = (ctx.getPackageTypes() != null
                        && !ctx.getPackageTypes().isEmpty())
                        ? ctx.getPackageTypes().get(0) : null;
                Money hubFee = computeHubStorageFee(policy, ctx.getStorageHours(), primaryType, currency);
                if (hubFee.isPositive()) {
                    current = current.add(hubFee);
                    breakdown.add(PriceLineItem.of(
                            String.format("Hub storage (%d h)", ctx.getStorageHours()),
                            LineItemType.HUB_STORAGE, hubFee));
                }
            }

            // ── Step 5:  — Network transit fee ───────────────────────────
            if (ctx.getNetworkHopCount() != null && ctx.getNetworkHopCount() > 0) {
                boolean isInterCity = "PERI_URBAN".equalsIgnoreCase(ctx.getDeliveryZoneType())
                        || "RURAL".equalsIgnoreCase(ctx.getDeliveryZoneType());
                Money transitFee = computeNetworkTransitFee(
                        policy, ctx.getNetworkHopCount(), isInterCity, currency);
                if (transitFee.isPositive()) {
                    current = current.add(transitFee);
                    breakdown.add(PriceLineItem.of(
                            String.format("Network transit (%d hop(s))", ctx.getNetworkHopCount()),
                            LineItemType.NETWORK_TRANSIT, transitFee));
                }
            }

            // ── Step 6: Loyalty discounts ─────────────────────────────────────
            Money totalDiscount = Money.of(BigDecimal.ZERO, currency);
            if (policy.getLoyaltyRules() != null) {
                for (LoyaltyRule lr : policy.getLoyaltyRules()) {
                    if (lr.isEligible(ctx)) {
                        Money discount = lr.computeDiscount(current);
                        current = current.subtract(discount);
                        totalDiscount = totalDiscount.add(discount);
                        breakdown.add(PriceLineItem.discount(
                                "Loyalty discount -" + lr.getDiscountPercentage() + "%",
                                LineItemType.LOYALTY, discount));
                    }
                }
            }

            // ── Step 7: Promotions ────────────────────────────────────────────
            LocalDateTime now = LocalDateTime.now();
            if (policy.getPromotions() != null) {
                for (Promotion promo : policy.getPromotions()) {
                    if (promo.isActive(now) && promo.hasRemainingUsages()
                            && promo.meetsMinimumAmount(current)) {
                        boolean conditionMet = promo.getConditionExpression() == null
                                || evaluateCondition(promo.getConditionExpression(), ctx);
                        if (conditionMet) {
                            Money discount = promo.computeDiscount(current);
                            totalDiscount = totalDiscount.add(discount);
                            current = current.subtract(discount);
                            breakdown.add(PriceLineItem.discount(
                                    "Promotion: " + promo.getName(),
                                    LineItemType.PROMOTION, discount));
                            appliedPromotionIds.add(promo.getId());
                        }
                    }
                }
            }

            // ── Step 8: Platform fee ─────────────────────────────────────────
            Money platformFee = Money.of(BigDecimal.ZERO, currency);
            if (policy.getPlatformFeeRule() != null) {
                platformFee = policy.getPlatformFeeRule().compute(current);
                breakdown.add(PriceLineItem.of("Platform fee", LineItemType.PLATFORM_FEE, platformFee));
            }

            // ── Step 9: Commission ────────────────────────────────────────────
            Money delivererCommission = Money.of(BigDecimal.ZERO, currency);
            Optional<CommissionRule> commissionRuleOpt = policy.findCommissionRule();
            if (commissionRuleOpt.isPresent()) {
                delivererCommission = commissionRuleOpt.get().computeDelivererCommission(current);
                breakdown.add(PriceLineItem.of("Deliverer commission",
                        LineItemType.COMMISSION, delivererCommission));
            }

            boolean marginNegative = current.isNegative();

            return PriceEvaluation.builder()
                    .sellingPrice(current)
                    .priceBreakdown(breakdown)
                    .appliedRuleId(matchedRule.getId())
                    .appliedSurchargeIds(appliedSurchargeIds)
                    .appliedPromotionIds(appliedPromotionIds)
                    .discountApplied(totalDiscount)
                    .platformFee(platformFee)
                    .delivererCommission(delivererCommission)
                    .isMarginNegative(marginNegative)
                    .computedAt(Instant.now())
                    .build();
        });
    }

    // Special surcharges evaluation ─────────────────────────────────

    /**
     * Evaluates all active special surcharge rules against the context and applies
     * the configured stacking mode.
     *
     * <p>Stacking modes:
     * <ul>
     *   <li>CUMULATIVE — all matching surcharges are summed.</li>
     *   <li>EXCLUSIVE_HIGHEST — only the largest matching surcharge applies.</li>
     *   <li>CAPPED — sum of matching surcharges is capped at the cap amount.</li>
     * </ul>
     *
     * @param rules     active special surcharge rules to evaluate
     * @param ctx       the pricing context
     * @param base      the current price (base for percentage surcharges)
     * @param breakdown mutable list to which triggered surcharge line items are appended
     * @return the total special surcharge amount to add to {@code base}
     */
    public Money evaluateSpecialSurcharges(List<SpecialSurchargeRule> rules,
                                            PricingContext ctx,
                                            Money base,
                                            List<PriceLineItem> breakdown) {
        String currency = base.getCurrency().getCurrencyCode();

        // Collect all matching surcharges
        record MatchedSurcharge(SpecialSurchargeRule rule, Money amount) {}
        List<MatchedSurcharge> matched = new ArrayList<>();

        for (SpecialSurchargeRule rule : rules) {
            if (!rule.isActive()) continue;
            if (evaluateCondition(rule.getTriggerCondition(), ctx)) {
                Money amount = rule.computeSurcharge(base);
                matched.add(new MatchedSurcharge(rule, amount));
                log.debug("Special surcharge '{}' triggered: {}", rule.getSurchargeCode(), amount);
            }
        }

        if (matched.isEmpty()) return Money.of(BigDecimal.ZERO, currency);

        // Determine the effective stacking mode from the first matched rule (policy-level default)
        SurchargeStackMode stackMode = matched.get(0).rule().getStackMode() != null
                ? matched.get(0).rule().getStackMode()
                : SurchargeStackMode.CUMULATIVE;

        return switch (stackMode) {
            case CUMULATIVE -> {
                Money total = Money.of(BigDecimal.ZERO, currency);
                for (MatchedSurcharge ms : matched) {
                    total = total.add(ms.amount());
                    breakdown.add(PriceLineItem.of(
                            "Special surcharge: " + ms.rule().getSurchargeCode(),
                            LineItemType.SPECIAL_SURCHARGE, ms.amount()));
                }
                yield total;
            }
            case EXCLUSIVE_HIGHEST -> {
                MatchedSurcharge highest = matched.stream()
                        .max(Comparator.comparing(ms -> ms.amount().getAmount()))
                        .orElseThrow();
                breakdown.add(PriceLineItem.of(
                        "Special surcharge (highest): " + highest.rule().getSurchargeCode(),
                        LineItemType.SPECIAL_SURCHARGE, highest.amount()));
                yield highest.amount();
            }
            case CAPPED -> {
                Money total = Money.of(BigDecimal.ZERO, currency);
                for (MatchedSurcharge ms : matched) {
                    total = total.add(ms.amount());
                }
                // Apply cap from the first rule that defines one
                BigDecimal capAmount = matched.stream()
                        .map(ms -> ms.rule().getCapAmount())
                        .filter(cap -> cap != null && cap.compareTo(BigDecimal.ZERO) > 0)
                        .findFirst()
                        .orElse(null);
                if (capAmount != null && total.getAmount().compareTo(capAmount) > 0) {
                    total = Money.of(capAmount, currency);
                }
                breakdown.add(PriceLineItem.of(
                        "Special surcharges (capped)",
                        LineItemType.SPECIAL_SURCHARGE, total));
                yield total;
            }
        };
    }

    /**
     * Computes the hub storage fee for a parcel stored for the given number of hours.
     *
     * @param policy       the billing policy with hub storage rules
     * @param storageHours hours the parcel has been in storage
     * @param packageType  type of the stored package (may be null)
     * @param currency     ISO 4217 currency code for the result
     * @return the storage fee, or zero if no applicable rule found
     */
    public Money computeHubStorageFee(BillingPolicy policy, int storageHours,
                                       PackageType packageType, String currency) {
        return policy.findHubStorageRule(storageHours, packageType)
                .map(rule -> rule.computeFee(storageHours, currency))
                .orElseGet(() -> Money.of(BigDecimal.ZERO, currency));
    }

    /**
     * Computes the total network transit fee for the given number of hops.
     *
     * @param policy    the billing policy with network transit rules
     * @param hopCount  number of relay hops
     * @param interCity whether the route crosses a city boundary
     * @param currency  ISO 4217 currency code
     * @return the total transit fee, or zero if no applicable rules found
     */
    public Money computeNetworkTransitFee(BillingPolicy policy, int hopCount,
                                           boolean interCity, String currency) {
        List<NetworkTransitRule> rules = policy.findNetworkTransitRules(hopCount, interCity);
        if (rules.isEmpty()) return Money.of(BigDecimal.ZERO, currency);
        return rules.stream()
                .map(rule -> rule.computeFee(hopCount, interCity, currency))
                .reduce(Money.of(BigDecimal.ZERO, currency), Money::add);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Optional<PricingRule> findMatchingPricingRule(BillingPolicy policy, PricingContext ctx) {
        return policy.sortedPricingRules().stream()
                .filter(rule -> evaluateCondition(rule.getConditionExpression(), ctx))
                .findFirst();
    }

    private boolean evaluateCondition(String conditionExpression, PricingContext ctx) {
        if (conditionExpression == null || conditionExpression.isBlank()) return true;
        try {
            AstNode ast = dslCompilerService.compileCondition(conditionExpression);
            return dslEvaluator.evaluate(ast, ctx);
        } catch (Exception e) {
            log.warn("DSL condition evaluation failed for expression '{}': {}",
                    conditionExpression, e.getMessage());
            return false;
        }
    }

    private Money applyMinMax(PricingRule rule, Money current) {
        Money result = current;
        if (rule.getMinimumPrice() != null
                && current.getAmount().compareTo(rule.getMinimumPrice().getAmount()) < 0) {
            result = rule.getMinimumPrice();
        }
        if (rule.getMaximumPrice() != null
                && result.getAmount().compareTo(rule.getMaximumPrice().getAmount()) > 0) {
            result = rule.getMaximumPrice();
        }
        return result;
    }
}
