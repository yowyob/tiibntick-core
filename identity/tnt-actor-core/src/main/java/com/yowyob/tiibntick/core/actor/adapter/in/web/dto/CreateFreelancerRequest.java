package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateFreelancerRequest(
        @NotNull List<UUID> serviceZoneIds,
        List<AvailabilitySlotDto> availabilitySlots,
        UUID pricingPolicyId) {
}
