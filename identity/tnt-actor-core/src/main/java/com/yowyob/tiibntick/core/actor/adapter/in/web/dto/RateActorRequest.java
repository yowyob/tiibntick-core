package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RateActorRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("5.0") double score,
        UUID ratedByActorId) {
}
