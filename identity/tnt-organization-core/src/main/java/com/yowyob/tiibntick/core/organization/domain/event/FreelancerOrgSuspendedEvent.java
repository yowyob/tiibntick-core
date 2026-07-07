package com.yowyob.tiibntick.core.organization.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a FreelancerOrganization is suspended.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code tnt-delivery-core} — blocks new mission assignments to this org.</li>
 *   <li>{@code tnt-notify-core}   — notifies the OWNER of the suspension.</li>
 * </ul>
 *
 * @param orgId        TiiBnTick internal FreelancerOrganization UUID
 * @param tenantId     Multi-tenant key
 * @param ownerActorId OWNER actor UUID
 * @param reason       Human-readable suspension reason
 * @param adminActorId Admin who triggered the suspension (nullable for self-suspension)
 * @param occurredAt   Event timestamp (UTC)
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgSuspendedEvent(
        UUID orgId,
        String tenantId,
        UUID ownerActorId,
        String reason,
        UUID adminActorId,
        Instant occurredAt
) {
    public static FreelancerOrgSuspendedEvent of(UUID orgId, String tenantId,
                                                  UUID ownerActorId, String reason,
                                                  UUID adminActorId) {
        return new FreelancerOrgSuspendedEvent(orgId, tenantId, ownerActorId,
                reason, adminActorId, Instant.now());
    }
}
