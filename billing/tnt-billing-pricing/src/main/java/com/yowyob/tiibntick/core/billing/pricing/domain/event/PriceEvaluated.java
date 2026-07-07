package com.yowyob.tiibntick.core.billing.pricing.domain.event;

import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
public class PriceEvaluated {
    UUID policyId;
    UUID tenantId;
    String missionId;
    BigDecimal sellingPrice;
    String currency;
    boolean marginNegative;
    Instant occurredAt;

    public static PriceEvaluated of(UUID policyId, UUID tenantId, String missionId,
                                     BigDecimal sellingPrice, String currency, boolean marginNegative) {
        return new PriceEvaluated(policyId, tenantId, missionId,
                sellingPrice, currency, marginNegative, Instant.now());
    }
}
