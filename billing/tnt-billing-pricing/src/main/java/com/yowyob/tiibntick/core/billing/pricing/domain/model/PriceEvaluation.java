package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PriceEvaluation {

    Money sellingPrice;
    List<PriceLineItem> priceBreakdown;

    UUID appliedRuleId;
    List<UUID> appliedSurchargeIds;
    List<UUID> appliedPromotionIds;

    Money discountApplied;
    Money platformFee;
    Money delivererCommission;

    boolean isMarginNegative;
    Instant computedAt;

    public boolean hasBaseRule() {
        return appliedRuleId != null;
    }

    public int totalLinesApplied() {
        return (priceBreakdown != null ? priceBreakdown.size() : 0);
    }

    public Money totalSurcharges() {
        if (priceBreakdown == null) return Money.zeroXAF();
        return priceBreakdown.stream()
                .filter(l -> com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.LineItemType.SURCHARGE.equals(l.getType()))
                .map(PriceLineItem::getAmount)
                .reduce(Money.zeroXAF(), Money::add);
    }

    public Money totalDiscounts() {
        if (priceBreakdown == null) return Money.zeroXAF();
        return priceBreakdown.stream()
                .filter(PriceLineItem::isDiscount)
                .map(PriceLineItem::getAmount)
                .reduce(Money.zeroXAF(), Money::add);
    }

    public static PriceEvaluation empty(String currency) {
        return PriceEvaluation.builder()
                .sellingPrice(Money.of(BigDecimal.ZERO, currency))
                .priceBreakdown(List.of())
                .appliedSurchargeIds(List.of())
                .appliedPromotionIds(List.of())
                .discountApplied(Money.of(BigDecimal.ZERO, currency))
                .platformFee(Money.of(BigDecimal.ZERO, currency))
                .delivererCommission(Money.of(BigDecimal.ZERO, currency))
                .isMarginNegative(false)
                .computedAt(Instant.now())
                .build();
    }
}
