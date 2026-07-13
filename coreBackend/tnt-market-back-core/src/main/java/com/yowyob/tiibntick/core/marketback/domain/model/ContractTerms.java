package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Value Object — commercial terms of a MerchantContract.
 * @author MANFOUO Braun
 */
public record ContractTerms(
        double baseDiscountPct,
        int maxMonthlyOrders,
        int minMonthlyOrders,
        int paymentTermDays,
        String dslExpressionOverride,
        String specialConditions
) {
    public double effectiveDiscount(int monthlyVolume) {
        if (monthlyVolume < minMonthlyOrders) return 0.0;
        return baseDiscountPct;
    }
}
