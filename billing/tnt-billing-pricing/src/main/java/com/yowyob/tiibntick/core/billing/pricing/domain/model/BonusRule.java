package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.BonusType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Jacksonized
@Builder
public class BonusRule {

    UUID id;
    String conditionExpression;
    BonusType bonusType;
    BigDecimal bonusValue;
    String description;

    public Money computeBonus(Money base) {
        if (BonusType.PERCENTAGE.equals(bonusType)) {
            return base.percentage(bonusValue.intValue());
        }
        return Money.of(bonusValue, base.getCurrency().getCurrencyCode());
    }
}
