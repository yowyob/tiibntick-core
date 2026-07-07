package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.DiscountType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Jacksonized
@Builder(toBuilder = true)
public class Promotion {

    UUID id;
    String name;
    String code;
    DiscountType discountType;
    BigDecimal discountValue;
    Money minimumAmount;
    LocalDateTime validFrom;
    LocalDateTime validTo;
    Integer maxUsagesTotal;
    Integer maxUsagesPerClient;
    int currentUsages;
    String conditionExpression;

    public boolean isActive(LocalDateTime now) {
        return !now.isBefore(validFrom) && (validTo == null || !now.isAfter(validTo));
    }

    public boolean hasRemainingUsages() {
        return maxUsagesTotal == null || currentUsages < maxUsagesTotal;
    }

    public boolean meetsMinimumAmount(Money price) {
        if (minimumAmount == null) return true;
        return price.getAmount().compareTo(minimumAmount.getAmount()) >= 0;
    }

    public Money computeDiscount(Money base) {
        if (DiscountType.PERCENTAGE.equals(discountType)) {
            return base.percentage(discountValue.intValue());
        }
        return Money.of(discountValue, base.getCurrency().getCurrencyCode());
    }

    public Money apply(Money base) {
        return base.subtract(computeDiscount(base));
    }

    public Promotion incrementUsage() {
        return this.toBuilder().currentUsages(this.currentUsages + 1).build();
    }
}
