package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.AvailabilitySlot;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;

import java.util.List;
import java.util.UUID;

public record CreateFreelancerProfileCommand(
        UUID tenantId,
        UUID actorId,
        List<ServiceZoneId> serviceZoneIds,
        List<AvailabilitySlot> availabilitySlots,
        UUID pricingPolicyId) {
}
