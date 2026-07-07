package com.yowyob.tiibntick.core.billing.pricing.domain.event;

import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
public class BillingPolicyDeactivated {
    UUID policyId;
    UUID tenantId;
    Instant occurredAt;

    public static BillingPolicyDeactivated of(UUID policyId, UUID tenantId) {
        return new BillingPolicyDeactivated(policyId, tenantId, Instant.now());
    }
}
