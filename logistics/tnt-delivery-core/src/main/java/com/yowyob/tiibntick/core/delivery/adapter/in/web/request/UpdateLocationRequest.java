package com.yowyob.tiibntick.core.delivery.adapter.in.web.request;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import jakarta.validation.constraints.NotNull;

/**
 * HTTP request body for a GPS location update from a delivery person.
 *
 * @author MANFOUO Braun
 */
public record UpdateLocationRequest(
        @NotNull Double latitude,
        @NotNull Double longitude
) {

    public GeoCoordinates toCoordinates() {
        return new GeoCoordinates(latitude, longitude);
    }
}
