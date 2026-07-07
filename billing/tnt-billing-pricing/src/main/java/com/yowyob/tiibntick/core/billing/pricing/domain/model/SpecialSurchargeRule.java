package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeStackMode;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A conditional surcharge rule evaluated by its DSL triggerCondition.
 *
 * <p>SpecialSurchargeRules are evaluated in addition to the standard SurchargeRules
 * and support three stacking modes via {@link SurchargeStackMode}.
 *
 * <p>Examples:
 * <pre>
 *   requiresRefrigeration == true  → ADD_FIXED 2500 XAF
 *   activeEquipmentTypes CONTAINS REFRIGERATED_BOX AND distance >= 20  → ADD_PCT 20
 *   dayOfWeek DAY_IS WEEKEND  → ADD_PCT 10
 * </pre>
 *
 * @author MANFOUO Braun
 */
@Value
@Jacksonized
@Builder(toBuilder = true)
public class SpecialSurchargeRule {

    UUID id;

    /**
     * Short unique code identifying this surcharge within the policy.
     * Examples: "REFRIGERATION", "NIGHT_DELIVERY", "WEEKEND_PREMIUM".
     */
    String surchargeCode;

    /** DSL expression that triggers this surcharge when it evaluates to {@code true}. */
    String triggerCondition;

    /** Whether the surcharge is a fixed amount or a percentage of the current price. */
    SurchargeType surchargeType;

    /**
     * Surcharge amount (fixed XAF amount) or rate (percentage, e.g. 15 for 15%).
     * Interpretation depends on {@link #surchargeType}.
     */
    BigDecimal value;

    /**
     * Stacking mode controlling how this surcharge interacts with other matching specials.
     * Default: {@link SurchargeStackMode#CUMULATIVE}.
     */
    SurchargeStackMode stackMode;

    /**
     * Maximum cumulative special-surcharge amount when {@link SurchargeStackMode#CAPPED}.
     * Null for CUMULATIVE and EXCLUSIVE_HIGHEST modes.
     */
    BigDecimal capAmount;

    /** Whether this rule is active. Inactive rules are skipped during evaluation. */
    boolean isActive;

    /** Human-readable description shown in the admin UI. */
    String description;

    /**
     * Computes the monetary amount of this surcharge applied to the given base price.
     *
     * @param base the current price before this surcharge
     * @return the surcharge amount (not the new price — call {@link #apply(Money)} for that)
     */
    public Money computeSurcharge(Money base) {
        if (SurchargeType.PERCENTAGE.equals(surchargeType)) {
            return base.percentage(value);
        }
        return Money.of(value, base.getCurrency().getCurrencyCode());
    }

    /**
     * Applies this surcharge to the given base price.
     *
     * @param base the current price
     * @return the new price after adding this surcharge
     */
    public Money apply(Money base) {
        return base.add(computeSurcharge(base));
    }
}
