package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDaoZoneRequest(
        @NotBlank String name,
        String description,
        @NotNull Double centerLatitude,
        @NotNull Double centerLongitude,
        @NotNull @DecimalMin("0.01") Double radiusKm
) {
}
