package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.DiscountType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object: InvoiceDiscount.
 * Represents a discount applied to an invoice (promotional, loyalty, or fixed).
 *
 * @author MANFOUO Braun
 */
public record InvoiceDiscount(
        String description,
        DiscountType discountType,
        BigDecimal value,
        Money appliedAmount
) {
    public InvoiceDiscount {
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(discountType, "discountType is required");
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(appliedAmount, "appliedAmount is required");
    }

    /**
     * Computes a percentage discount applied to a base amount.
     */
    public static InvoiceDiscount percentage(String description, BigDecimal pct, Money base) {
        return new InvoiceDiscount(description, DiscountType.PERCENTAGE, pct, base.percentage(pct));
    }

    /**
     * Computes a fixed-amount discount.
     */
    public static InvoiceDiscount fixed(String description, Money amount) {
        return new InvoiceDiscount(description, DiscountType.FIXED_AMOUNT, amount.amount(), amount);
    }

    /**
     * Computes a loyalty-point discount given a redemption amount in currency.
     */
    public static InvoiceDiscount loyaltyPoints(String description, Money redemptionAmount) {
        return new InvoiceDiscount(description, DiscountType.LOYALTY_POINTS,
                redemptionAmount.amount(), redemptionAmount);
    }
}
