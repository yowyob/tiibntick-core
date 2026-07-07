package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.LineItemType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PriceLineItem {
    String label;
    LineItemType type;
    Money amount;
    boolean isDiscount;

    public static PriceLineItem of(String label, LineItemType type, Money amount) {
        return PriceLineItem.builder()
                .label(label)
                .type(type)
                .amount(amount)
                .isDiscount(false)
                .build();
    }

    public static PriceLineItem discount(String label, LineItemType type, Money amount) {
        return PriceLineItem.builder()
                .label(label)
                .type(type)
                .amount(amount)
                .isDiscount(true)
                .build();
    }
}
