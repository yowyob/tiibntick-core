package com.yowyob.tiibntick.core.agency.workforce.domain.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ported from tnt-agency {@code CommissionRateNormalizer}. */
public final class CommissionRateNormalizer {

    private CommissionRateNormalizer() {}

    public static BigDecimal toPercentPoints(BigDecimal rate) {
        if (rate == null) return null;
        if (rate.compareTo(BigDecimal.ZERO) <= 0) return rate.setScale(2, RoundingMode.HALF_UP);
        if (rate.compareTo(BigDecimal.ONE) <= 0) {
            return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }
        return rate.setScale(2, RoundingMode.HALF_UP);
    }
}
