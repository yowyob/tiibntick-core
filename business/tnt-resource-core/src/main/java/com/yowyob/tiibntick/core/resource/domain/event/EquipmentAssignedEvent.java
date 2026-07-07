package com.yowyob.tiibntick.core.resource.domain.event;

import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when equipment is assigned to a field agent.
 * @author MANFOUO Braun.
 */
public record EquipmentAssignedEvent(
        UUID eventId,
        UUID equipmentId,
        UUID tenantId,
        UUID branchId,
        EquipmentType equipmentType,
        UUID assignedUserId,
        Instant occurredAt
) {
    public static EquipmentAssignedEvent of(UUID equipmentId, UUID tenantId, UUID branchId,
            EquipmentType type, UUID assignedUserId) {
        return new EquipmentAssignedEvent(UUID.randomUUID(), equipmentId, tenantId, branchId,
                type, assignedUserId, Instant.now());
    }
}
