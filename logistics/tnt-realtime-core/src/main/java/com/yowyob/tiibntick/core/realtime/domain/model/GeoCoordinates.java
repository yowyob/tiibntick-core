package com.yowyob.tiibntick.core.realtime.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Geographic coordinates value object used throughout the realtime domain.
 * Represents a WGS-84 coordinate pair (latitude, longitude).
 * Optionally includes altitude in meters and accuracy in meters.
 *
 * <p>This type is deliberately redefined here to keep tnt-realtime-core
 * self-contained. A mapper handles conversion to/from tnt-geo-core's GeoPoint
 * when interacting with geo-core services.</p>
 *
 * @author MANFOUO Braun
 */
public record GeoCoordinates(
        double latitude,
        double longitude,
        Double altitude,
        Double accuracyMeters
) {

    /** Earth radius in kilometers (WGS-84 mean). */
    private static final double EARTH_RADIUS_KM = 6371.0;

    @JsonCreator
    public static GeoCoordinates of(
            @JsonProperty("latitude") double latitude,
            @JsonProperty("longitude") double longitude,
            @JsonProperty("altitude") Double altitude,
            @JsonProperty("accuracyMeters") Double accuracyMeters) {
        return new GeoCoordinates(latitude, longitude, altitude, accuracyMeters);
    }

    public static GeoCoordinates of(double latitude, double longitude) {
        return new GeoCoordinates(latitude, longitude, null, null);
    }

    /**
     * Computes the great-circle distance (Haversine formula) between this
     * coordinate and another, in kilometers.
     *
     * @param other the target coordinates
     * @return distance in kilometers
     */
    public double distanceKmTo(GeoCoordinates other) {
        Objects.requireNonNull(other, "Target coordinates must not be null");
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Checks that the coordinates are within valid WGS-84 ranges.
     *
     * @return true if the coordinates are geographically valid
     */
    public boolean isValid() {
        return latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    @Override
    public String toString() {
        return String.format("GeoCoordinates{lat=%.6f, lon=%.6f}", latitude, longitude);
    }

    public double lastLat() {
        return latitude;
    }

    public double lastLong() {
        return longitude;
    }
}
