package com.yowyob.tiibntick.core.organization.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a sub-deliverer's association is revoked.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code tnt-actor-core}  — unlinks FreelancerOrganization from the actor profile.</li>
 *   <li>{@code tnt-notify-core} — notifies both the OWNER and the sub-deliverer.</li>
 * </ul>
 *
 * @param orgId          TiiBnTick internal FreelancerOrganization UUID
 * @param tenantId       Multi-tenant key
 * @param ownerActorId   OWNER actor UUID
 * @param subDelivererId Revoked sub-deliverer actor UUID
 * @param occurredAt     Event timestamp (UTC)
 *
 * @author MANFOUO Braun
 */
public record SubDelivererRevokedEvent(
        UUID orgId,
        String tenantId,
        UUID ownerActorId,
        UUID subDelivererId,
        Instant occurredAt
) {
    public static SubDelivererRevokedEvent of(UUID orgId, String tenantId,
                                               UUID ownerActorId, UUID subDelivererId) {
        return new SubDelivererRevokedEvent(orgId, tenantId, ownerActorId,
                subDelivererId, Instant.now());
    }
}
