package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.LocationSource;
import jakarta.validation.constraints.NotNull;

public record UpdateActorLocationRequest(
        @NotNull double latitude,
        @NotNull double longitude,
        Double accuracy,
        LocationSource source) {

    public LocationSource resolvedSource() {
        return source != null ? source : LocationSource.GPS;
    }
}
