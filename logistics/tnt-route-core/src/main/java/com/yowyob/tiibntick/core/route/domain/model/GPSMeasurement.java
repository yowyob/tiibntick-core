package com.yowyob.tiibntick.core.route.domain.model;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import java.time.Instant;
import java.util.Objects;

public record GPSMeasurement(
        GeoPoint coordinates,
        double speedKmh,
        double bearing,
        double accuracyMetres,
        Instant timestamp
) {
    public GPSMeasurement {
        Objects.requireNonNull(coordinates, "coordinates required");
        Objects.requireNonNull(timestamp, "timestamp required");
        if (speedKmh < 0) throw new IllegalArgumentException("speedKmh must be >= 0");
        if (accuracyMetres < 0) throw new IllegalArgumentException("accuracy must be >= 0");
    }

    public double distanceTo(GeoPoint other) {
        return coordinates.haversineDistanceTo(other);
    }
}
