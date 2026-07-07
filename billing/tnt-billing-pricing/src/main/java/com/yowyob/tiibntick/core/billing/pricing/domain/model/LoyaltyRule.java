package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Jacksonized
@Builder
public class LoyaltyRule {

    UUID id;
    int minimumTransactionCount;
    int periodDays;
    BigDecimal minimumTotalSpentXAF;
    BigDecimal discountPercentage;

    public boolean isEligible(PricingContext ctx) {
        return ctx.getClientTxCount() >= minimumTransactionCount;
    }

    public Money apply(Money base) {
        return base.subtract(base.percentage(discountPercentage.intValue()));
    }

    public Money computeDiscount(Money base) {
        return base.percentage(discountPercentage.intValue());
    }
}
