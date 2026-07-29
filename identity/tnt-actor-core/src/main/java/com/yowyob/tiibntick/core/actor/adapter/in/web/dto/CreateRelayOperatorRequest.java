package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record CreateRelayOperatorRequest(
        @NotNull UUID hubId,
        List<AvailabilitySlotDto> openingHours,
        @PositiveOrZero int declaredCapacityParcels) {
}
