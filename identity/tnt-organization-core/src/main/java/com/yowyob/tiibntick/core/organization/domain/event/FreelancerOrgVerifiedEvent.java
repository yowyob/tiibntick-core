package com.yowyob.tiibntick.core.organization.domain.event;

import com.yowyob.tiibntick.core.organization.domain.enums.KycLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a FreelancerOrganization is verified by an admin.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code tnt-trust}       — issues the blockchain DID document.</li>
 *   <li>{@code tnt-notify-core} — sends verification success notification.</li>
 *   <li>{@code tnt-actor-core}  — updates the linked actor profile badge.</li>
 * </ul>
 *
 * @param orgId         TiiBnTick internal FreelancerOrganization UUID
 * @param tenantId      Multi-tenant key
 * @param ownerActorId  OWNER actor UUID
 * @param kycLevel      KYC level at time of verification
 * @param adminActorId  Admin who performed the verification
 * @param occurredAt    Event timestamp (UTC)
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgVerifiedEvent(
        UUID orgId,
        String tenantId,
        UUID ownerActorId,
        KycLevel kycLevel,
        UUID adminActorId,
        Instant occurredAt
) {
    public static FreelancerOrgVerifiedEvent of(UUID orgId, String tenantId,
                                                 UUID ownerActorId, KycLevel kycLevel,
                                                 UUID adminActorId) {
        return new FreelancerOrgVerifiedEvent(orgId, tenantId, ownerActorId,
                kycLevel, adminActorId, Instant.now());
    }
}
