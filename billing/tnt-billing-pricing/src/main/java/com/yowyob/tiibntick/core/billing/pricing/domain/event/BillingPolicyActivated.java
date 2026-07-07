package com.yowyob.tiibntick.core.billing.pricing.domain.event;

import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
public class BillingPolicyActivated {
    UUID policyId;
    UUID tenantId;
    Instant occurredAt;

    public static BillingPolicyActivated of(UUID policyId, UUID tenantId) {
        return new BillingPolicyActivated(policyId, tenantId, Instant.now());
    }
}
