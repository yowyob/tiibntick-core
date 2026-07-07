package com.yowyob.tiibntick.core.incident.domain.valueobject;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Pricing delta applied when a replacement driver or vehicle is dispatched.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value
@Builder
public class PricingAdjustment {
    BigDecimal originalPriceXAF;
    BigDecimal adjustedPriceXAF;
    BigDecimal extraKmFee;
    BigDecimal urgencyFee;
    String adjustmentReason;

    public BigDecimal totalAdjustment() {
        return adjustedPriceXAF.subtract(originalPriceXAF);
    }

    public boolean hasIncrease() {
        return adjustedPriceXAF.compareTo(originalPriceXAF) > 0;
    }
}
