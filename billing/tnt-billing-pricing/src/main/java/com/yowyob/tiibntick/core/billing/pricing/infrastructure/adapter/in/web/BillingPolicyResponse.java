package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Outbound REST DTO for a billing policy.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #ownerType} — owner type of the policy</li>
 *   <li>{@link #ownerActorId} — owner actor UUID string</li>
 *   <li>{@link #isFromTemplate} — template flag</li>
 *   <li>{@link #templateCode} — template code</li>
 *   <li>{@link #dslAccessLevel} — DSL access level</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record BillingPolicyResponse(
        UUID id,
        UUID tenantId,
        UUID agencyId,
        String name,
        String description,
        PolicyStatus status,
        boolean isDefault,
        LocalDate validFrom,
        LocalDate validTo,
        Instant createdAt,
        Instant updatedAt,
        //  — owner metadata
        PolicyOwnerType ownerType,
        String ownerActorId,
        boolean isFromTemplate,
        String templateCode,
        DslAccessLevel dslAccessLevel
) {
    public static BillingPolicyResponse from(BillingPolicy policy) {
        return new BillingPolicyResponse(
                policy.getId(),
                policy.getTenantId(),
                policy.getAgencyId(),
                policy.getName(),
                policy.getDescription(),
                policy.getStatus(),
                policy.isDefault(),
                policy.getValidFrom(),
                policy.getValidTo(),
                policy.getCreatedAt(),
                policy.getUpdatedAt(),
                // 
                policy.getOwnerType(),
                policy.getOwnerActorId(),
                policy.isFromTemplate(),
                policy.getTemplateCode(),
                policy.getDslAccessLevel()
        );
    }
}
