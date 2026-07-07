package com.yowyob.tiibntick.core.billing.dsl.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The complete result of evaluating a list of {@link DslRule} instances against a
 * {@link PricingContext}.
 * <p>
 * Breakdown:
 * <ul>
 *   <li>{@code basePrice}   — base amount set by the first matching base rule</li>
 *   <li>{@code perKmTotal}  — distance component (perKmRate × distanceKm)</li>
 *   <li>{@code perKgTotal}  — weight component (perKgRate × weightKg)</li>
 *   <li>{@code surcharges}  — list of additive amounts from surcharge rules</li>
 *   <li>{@code discounts}   — list of deductions from loyalty / promo rules</li>
 *   <li>{@code finalPrice}  — net total = base + perKm + perKg + Σsurcharges − Σdiscounts</li>
 *   <li>{@code appliedRules} — ordered audit trail of every rule that fired</li>
 * </ul>
 * </p>
 *
 * @author MANFOUO Braun
 */
@Value
@Builder
public class EvaluationResult {

    Money basePrice;
    Money perKmTotal;
    Money perKgTotal;
    List<Money> surcharges;
    List<Money> discounts;
    Money finalPrice;
    List<AppliedRuleRecord> appliedRules;
    String currencyCode;

    /**
     * Returns {@code true} if at least one rule was applied (base rule matched).
     */
    public boolean hasMatch() {
        return !appliedRules.isEmpty();
    }

    /**
     * Returns the number of rules that fired during evaluation.
     */
    public int matchedRuleCount() {
        return appliedRules.size();
    }

    /**
     * Computes the total surcharge amount.
     */
    public Money totalSurcharges() {
        return surcharges.stream()
                .reduce(Money.of(0, currencyCode), Money::add);
    }

    /**
     * Computes the total discount amount.
     */
    public Money totalDiscounts() {
        return discounts.stream()
                .reduce(Money.of(0, currencyCode), Money::add);
    }
}
