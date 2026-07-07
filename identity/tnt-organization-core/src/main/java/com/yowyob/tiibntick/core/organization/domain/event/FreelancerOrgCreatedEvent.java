package com.yowyob.tiibntick.core.organization.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new FreelancerOrganization is registered.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code tnt-trust}        — prepares a DID document for the org.</li>
 *   <li>{@code tnt-billing-wallet} — creates the org wallet.</li>
 *   <li>{@code tnt-notify-core}  — sends a welcome notification.</li>
 * </ul>
 *
 * @param orgId        TiiBnTick internal FreelancerOrganization UUID
 * @param tenantId     Multi-tenant key (prefixed "FRL-")
 * @param tradeName    Commercial trade name
 * @param ownerActorId OWNER actor UUID
 * @param occurredAt   Event timestamp (UTC)
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgCreatedEvent(
        UUID orgId,
        String tenantId,
        String tradeName,
        UUID ownerActorId,
        Instant occurredAt
) {
    public static FreelancerOrgCreatedEvent of(UUID orgId, String tenantId,
                                                String tradeName, UUID ownerActorId) {
        return new FreelancerOrgCreatedEvent(orgId, tenantId, tradeName,
                ownerActorId, Instant.now());
    }
}
