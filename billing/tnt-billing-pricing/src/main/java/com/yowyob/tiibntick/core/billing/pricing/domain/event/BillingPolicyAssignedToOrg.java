package com.yowyob.tiibntick.core.billing.pricing.domain.event;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a {@link com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy}
 * is assigned to a FreelancerOrganization or another actor.
 *
 * @author MANFOUO Braun
 */
@Value
@Builder
public class BillingPolicyAssignedToOrg {

    UUID eventId;
    UUID policyId;
    String orgId;
    PolicyOwnerType orgType;
    Instant occurredAt;

    public static BillingPolicyAssignedToOrg of(UUID policyId, String orgId, PolicyOwnerType orgType) {
        return BillingPolicyAssignedToOrg.builder()
                .eventId(UUID.randomUUID())
                .policyId(policyId)
                .orgId(orgId)
                .orgType(orgType)
                .occurredAt(Instant.now())
                .build();
    }
}
