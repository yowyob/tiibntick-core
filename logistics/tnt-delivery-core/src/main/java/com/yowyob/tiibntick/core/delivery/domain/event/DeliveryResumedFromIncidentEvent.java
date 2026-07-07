package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a delivery is resumed after an incident was resolved.
 *
 * <p>Published when {@code MissionStatusPortAdapter.resumeMission()} is called,
 * typically triggered by tnt-incident-core's IncidentResolutionService.
 *
 * @author MANFOUO Braun
 */
public record DeliveryResumedFromIncidentEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID newDeliveryPersonId,
        UUID newVehicleId,
        String resumedToStatus,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public DeliveryResumedFromIncidentEvent(UUID deliveryId, UUID tenantId,
                                             UUID newDeliveryPersonId, UUID newVehicleId,
                                             String resumedToStatus, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId,
                newDeliveryPersonId, newVehicleId, resumedToStatus, occurredAt);
    }
}
