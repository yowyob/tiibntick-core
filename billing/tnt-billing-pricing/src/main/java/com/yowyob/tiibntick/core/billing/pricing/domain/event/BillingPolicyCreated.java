package com.yowyob.tiibntick.core.billing.pricing.domain.event;

import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
public class BillingPolicyCreated {
    UUID policyId;
    UUID tenantId;
    UUID agencyId;
    String policyName;
    boolean isDefault;
    Instant occurredAt;

    public static BillingPolicyCreated of(UUID policyId, UUID tenantId, UUID agencyId,
                                          String name, boolean isDefault) {
        return new BillingPolicyCreated(policyId, tenantId, agencyId, name, isDefault, Instant.now());
    }
}
