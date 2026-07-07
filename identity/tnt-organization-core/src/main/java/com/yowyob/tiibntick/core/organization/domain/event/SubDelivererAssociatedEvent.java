package com.yowyob.tiibntick.core.organization.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a sub-deliverer invitation is accepted (association becomes ACTIVE).
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code tnt-actor-core}  — links FreelancerOrganization to the actor profile.</li>
 *   <li>{@code tnt-notify-core} — notifies the OWNER and the sub-deliverer.</li>
 * </ul>
 *
 * @param orgId            TiiBnTick internal FreelancerOrganization UUID
 * @param tenantId         Multi-tenant key
 * @param ownerActorId     OWNER actor UUID
 * @param subDelivererId   Sub-deliverer actor UUID
 * @param commissionRate   Agreed commission rate (0.0–1.0)
 * @param occurredAt       Event timestamp (UTC)
 *
 * @author MANFOUO Braun
 */
public record SubDelivererAssociatedEvent(
        UUID orgId,
        String tenantId,
        UUID ownerActorId,
        UUID subDelivererId,
        BigDecimal commissionRate,
        Instant occurredAt
) {
    public static SubDelivererAssociatedEvent of(UUID orgId, String tenantId,
                                                  UUID ownerActorId, UUID subDelivererId,
                                                  BigDecimal commissionRate) {
        return new SubDelivererAssociatedEvent(orgId, tenantId, ownerActorId,
                subDelivererId, commissionRate, Instant.now());
    }
}
