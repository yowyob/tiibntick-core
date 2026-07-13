package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.Map;

/**
 * Value Object — detailed pricing breakdown of a MarketOrder.
 * @author MANFOUO Braun
 */
public record OrderPricing(
        Money baseAmount,
        Money distanceFee,
        Money weightFee,
        Money insuranceFee,
        Money expressFee,
        Money discount,
        Money totalAmount,
        String currency,
        Map<String, Money> breakdown
) {
    public boolean hasDiscount() {
        return discount != null && !discount.isZero();
    }

    public Money total() {
        return totalAmount;
    }

    public static OrderPricing fromEstimate(Money estimate) {
        return new OrderPricing(estimate, Money.zeroXaf(), Money.zeroXaf(),
                Money.zeroXaf(), Money.zeroXaf(), Money.zeroXaf(), estimate,
                estimate.currency(), Map.of("base", estimate));
    }
}
