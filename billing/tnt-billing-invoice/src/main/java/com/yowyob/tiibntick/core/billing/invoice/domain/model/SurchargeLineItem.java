package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * SurchargeLineItem — value object representing a single surcharge detail line
 * on a FreelancerOrg or Agency invoice.
 *
 * <p>Displayed on the invoice PDF as a human-readable breakdown of surcharges applied
 * by the billing DSL (FRAGILE, PERISHABLE_REFRIGERATED, NIGHT, WEEKEND, etc.).</p>
 *
 * <p>Reference: {@code 04_TNT_Modules_Impacted.md} §5.4 — tnt-billing-invoice</p>
 *
 * @author MANFOUO Braun
 */
public record SurchargeLineItem(
        /**
         * Unique business code for this surcharge.
         * Examples: {@code FRAGILE}, {@code NIGHT_SURCHARGE}, {@code RAIN_HEAVY},
         * {@code REFRIGERATED_BOX}, {@code EXPRESS_2H}, {@code LOYALTY_GOLD}.
         */
        String surchargeCode,

        /** Human-readable label in French — displayed on invoice for Cameroonian clients. */
        String labelFr,

        /** Human-readable label in English — used for international clients. */
        String labelEn,

        /**
         * Amount of this surcharge (XAF or %).
         * Positive = surcharge (client pays more). Negative = discount.
         */
        BigDecimal amount,

        /**
         * Display unit: {@code "XAF"} for fixed amounts, {@code "%"} for percentage surcharges.
         */
        String unit
) {
    public SurchargeLineItem {
        Objects.requireNonNull(surchargeCode, "surchargeCode is required");
        Objects.requireNonNull(amount, "amount is required");
        if (unit == null || unit.isBlank()) unit = "XAF";
    }

    /** Creates a fixed-amount surcharge item. */
    public static SurchargeLineItem fixed(String code, String labelFr, String labelEn, BigDecimal amountXAF) {
        return new SurchargeLineItem(code, labelFr, labelEn, amountXAF, "XAF");
    }

    /** Creates a percentage-based surcharge item. */
    public static SurchargeLineItem percentage(String code, String labelFr, String labelEn, BigDecimal pct) {
        return new SurchargeLineItem(code, labelFr, labelEn, pct, "%");
    }

    /** Returns true if this item is a discount (negative amount). */
    public boolean isDiscount() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
}
