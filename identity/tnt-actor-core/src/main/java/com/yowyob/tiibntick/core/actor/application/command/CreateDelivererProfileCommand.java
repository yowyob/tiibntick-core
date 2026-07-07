package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.DelivererType;

import java.util.UUID;

public record CreateDelivererProfileCommand(
        UUID tenantId,
        UUID actorId,
        UUID agencyId,
        UUID branchId,
        double capacityKg,
        DelivererType delivererType,
        UUID contractId) {
}
