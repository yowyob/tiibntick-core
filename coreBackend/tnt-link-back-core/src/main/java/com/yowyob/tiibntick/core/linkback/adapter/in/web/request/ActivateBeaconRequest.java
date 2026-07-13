package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ActivateBeaconRequest(
        String message,
        @NotNull @Positive Double radiusKm,
        @NotNull @Positive Integer durationMinutes
) {
}
