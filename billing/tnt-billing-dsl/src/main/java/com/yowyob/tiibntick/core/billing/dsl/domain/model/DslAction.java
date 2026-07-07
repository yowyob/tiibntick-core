package com.yowyob.tiibntick.core.billing.dsl.domain.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Represents a single action to apply when a DSL rule condition evaluates to {@code true}.
 * <p>
 * The action consists of a type (e.g. ADD_FIXED, DISCOUNT_PCT), a numeric value,
 * and optionally a currency code for monetary types.
 * </p>
 *
 * <p>Example serialisation:</p>
 * <pre>
 *   ACTION ADD_FIXED 500 XAF
 *   ACTION SET_PER_KM 50 XAF
 *   ACTION DISCOUNT_PCT 5
 *   ACTION ADD_PCT 15
 * </pre>
 *
 * @author MANFOUO Braun
 */
@Value
@Builder
@Jacksonized
public class DslAction {

    /** What kind of transformation to apply to the running price. */
    DslActionType actionType;

    /** Numeric magnitude of the action (amount, percentage, rate). */
    BigDecimal value;

    /**
     * ISO 4217 currency code for monetary actions (ADD_FIXED, SET_BASE, SET_PER_KM …).
     * {@code null} for ratio-based actions (ADD_PCT, DISCOUNT_PCT).
     */
    String currencyCode;

    /**
     * Convenience factory for a fixed monetary surcharge.
     *
     * @param amount       amount in the given currency
     * @param currencyCode ISO 4217 currency code (e.g. "XAF")
     * @return a new DslAction of type ADD_FIXED
     */
    public static DslAction addFixed(BigDecimal amount, String currencyCode) {
        return DslAction.builder()
                .actionType(DslActionType.ADD_FIXED)
                .value(amount)
                .currencyCode(currencyCode)
                .build();
    }

    /**
     * Convenience factory for a percentage surcharge.
     *
     * @param percentage percentage (e.g. 15 for 15%)
     * @return a new DslAction of type ADD_PCT
     */
    public static DslAction addPct(BigDecimal percentage) {
        return DslAction.builder()
                .actionType(DslActionType.ADD_PCT)
                .value(percentage)
                .build();
    }

    /**
     * Convenience factory for a percentage discount.
     *
     * @param percentage percentage (e.g. 5 for 5%)
     * @return a new DslAction of type DISCOUNT_PCT
     */
    public static DslAction discountPct(BigDecimal percentage) {
        return DslAction.builder()
                .actionType(DslActionType.DISCOUNT_PCT)
                .value(percentage)
                .build();
    }

    /**
     * Convenience factory for setting the base price.
     *
     * @param amount       base price amount
     * @param currencyCode ISO 4217 currency code
     * @return a new DslAction of type SET_BASE
     */
    public static DslAction setBase(BigDecimal amount, String currencyCode) {
        return DslAction.builder()
                .actionType(DslActionType.SET_BASE)
                .value(amount)
                .currencyCode(currencyCode)
                .build();
    }

    /**
     * Convenience factory for setting the per-km rate.
     *
     * @param rate         rate per kilometre
     * @param currencyCode ISO 4217 currency code
     * @return a new DslAction of type SET_PER_KM
     */
    public static DslAction setPerKm(BigDecimal rate, String currencyCode) {
        return DslAction.builder()
                .actionType(DslActionType.SET_PER_KM)
                .value(rate)
                .currencyCode(currencyCode)
                .build();
    }
}
