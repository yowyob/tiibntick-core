package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.FeeType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Jacksonized
@Builder
public class PlatformFeeRule {

    FeeType feeType;
    BigDecimal feeValue;
    Money minimumFee;

    public Money compute(Money sellingPrice) {
        Money computed;
        if (FeeType.PERCENTAGE.equals(feeType)) {
            computed = sellingPrice.percentage(feeValue.intValue());
        } else {
            computed = Money.of(feeValue, sellingPrice.getCurrency().getCurrencyCode());
        }
        if (minimumFee != null && computed.getAmount().compareTo(minimumFee.getAmount()) < 0) {
            return minimumFee;
        }
        return computed;
    }
}
