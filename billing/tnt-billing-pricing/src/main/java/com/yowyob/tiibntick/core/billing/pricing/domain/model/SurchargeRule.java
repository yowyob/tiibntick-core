package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Jacksonized
@Builder
public class SurchargeRule {

    UUID id;
    String name;
    String conditionExpression;
    SurchargeType surchargeType;
    BigDecimal value;
    String description;

    public Money apply(Money base) {
        if (SurchargeType.PERCENTAGE.equals(surchargeType)) {
            return base.add(base.percentage(value.intValue()));
        }
        return base.add(Money.of(value, base.getCurrency().getCurrencyCode()));
    }

    public Money computeSurcharge(Money base) {
        if (SurchargeType.PERCENTAGE.equals(surchargeType)) {
            return base.percentage(value.intValue());
        }
        return Money.of(value, base.getCurrency().getCurrencyCode());
    }
}
