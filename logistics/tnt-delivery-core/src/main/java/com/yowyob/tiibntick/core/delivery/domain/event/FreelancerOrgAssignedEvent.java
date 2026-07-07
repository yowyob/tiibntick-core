package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a FreelancerOrganization is assigned to execute a delivery.
 *
 * <p>Published to Kafka topic: {@code tnt.delivery.freelancer_org.assigned}
 * Consumed by: tnt-billing-wallet (for revenue split setup),
 *              tnt-notify-core (notify OWNER/SUB_DELIVERER).
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgAssignedEvent(
        UUID eventId,
        UUID deliveryId,
        UUID tenantId,
        String freelancerOrgId,
        /** OWNER or SUB_DELIVERER */
        String freelancerRole,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public FreelancerOrgAssignedEvent(UUID deliveryId, UUID tenantId, String freelancerOrgId, String freelancerRole, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, freelancerOrgId, freelancerRole, occurredAt);
    }

    @Override public UUID aggregateId() { return deliveryId; }
}
