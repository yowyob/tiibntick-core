package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.DelivererType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateDelivererRequest(
        @NotNull UUID agencyId,
        @NotNull UUID branchId,
        @Positive double capacityKg,
        DelivererType delivererType,
        UUID contractId) {
}
