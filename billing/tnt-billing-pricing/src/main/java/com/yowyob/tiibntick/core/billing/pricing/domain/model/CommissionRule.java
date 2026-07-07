package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.CommissionAppliesTo;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
@Jacksonized
@Builder
public class CommissionRule {

    UUID id;
    CommissionAppliesTo applyToType;
    BigDecimal platformCommissionPct;
    BigDecimal agencyCommissionPct;
    BigDecimal delivererBaseCommissionPct;
    List<BonusRule> bonusRules;

    public Money computeDelivererCommission(Money sellingPrice) {
        return sellingPrice.percentage(delivererBaseCommissionPct.intValue());
    }

    public Money computePlatformFee(Money sellingPrice) {
        return sellingPrice.percentage(platformCommissionPct.intValue());
    }

    public Money computeAgencyCommission(Money sellingPrice) {
        if (agencyCommissionPct == null) return Money.zeroXAF();
        return sellingPrice.percentage(agencyCommissionPct.intValue());
    }

    public boolean appliesTo(CommissionAppliesTo targetType) {
        return CommissionAppliesTo.ALL.equals(applyToType) || applyToType.equals(targetType);
    }
}
