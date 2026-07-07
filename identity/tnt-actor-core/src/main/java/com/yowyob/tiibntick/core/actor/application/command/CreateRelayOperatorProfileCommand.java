package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.AvailabilitySlot;

import java.util.List;
import java.util.UUID;

public record CreateRelayOperatorProfileCommand(
        UUID tenantId,
        UUID actorId,
        UUID hubId,
        List<AvailabilitySlot> openingHours,
        int declaredCapacityParcels) {
}
