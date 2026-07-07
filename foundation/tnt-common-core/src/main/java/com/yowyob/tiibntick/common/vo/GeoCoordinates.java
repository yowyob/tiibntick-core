package com.yowyob.tiibntick.common.vo;

import java.util.Objects;

/**
 * Immutable value object representing a geographic coordinate pair (latitude/longitude).
 *
 * <p>Coordinates follow the WGS-84 standard (same as GPS and OpenStreetMap).
 * Latitude ranges from -90 to 90 (south to north).
 * Longitude ranges from -180 to 180 (west to east).
 *
 * <p>Used throughout TiiBnTick for:
 * <ul>
 *   <li>Package pickup / delivery locations</li>
 *   <li>Relay point positions (TiiBnTick Point)</li>
 *   <li>Deliverer real-time GPS positions</li>
 *   <li>Route waypoints in {@code tnt-route-core}</li>
 * </ul>
 *
 * Author: MANFOUO Braun
 */
public final class GeoCoordinates {

    /** Earth radius in meters (WGS-84 mean radius). */
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final double latitude;
    private final double longitude;

    private GeoCoordinates(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be in [-90, 90], got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be in [-180, 180], got: " + longitude);
        }
        this.latitude  = latitude;
        this.longitude = longitude;
    }

    /**
     * Creates a {@code GeoCoordinates} with validation.
     *
     * @param latitude  decimal degrees, WGS-84
     * @param longitude decimal degrees, WGS-84
     */
    public static GeoCoordinates of(double latitude, double longitude) {
        return new GeoCoordinates(latitude, longitude);
    }

    /**
     * Parses a comma-separated string {@code "lat,lon"}.
     *
     * @param latLon e.g., "3.8480,11.5021" (Yaoundé city center)
     * @throws IllegalArgumentException if format is invalid
     */
    public static GeoCoordinates parse(String latLon) {
        Objects.requireNonNull(latLon, "latLon must not be null");
        String[] parts = latLon.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected 'lat,lon' format, got: " + latLon);
        }
        try {
            return of(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric values in: " + latLon, e);
        }
    }

    /**
     * Computes the great-circle (haversine) distance to {@code other} in meters.
     *
     * <p>Accuracy is sufficient for logistics routing in African cities; for high-precision
     * road-network calculations use PostGIS {@code ST_Distance} in {@code tnt-geo-core}.
     *
     * @param other target coordinate — must not be null
     * @return distance in meters
     */
    public double haversineDistanceTo(GeoCoordinates other) {
        Objects.requireNonNull(other, "other coordinates must not be null");
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Returns the distance to {@code other} in kilometers.
     */
    public double distanceKmTo(GeoCoordinates other) {
        return haversineDistanceTo(other) / 1000.0;
    }

    /**
     * Returns {@code true} if this coordinate is within {@code radiusMeters} of {@code center}.
     */
    public boolean isWithinRadius(GeoCoordinates center, double radiusMeters) {
        return haversineDistanceTo(center) <= radiusMeters;
    }

    public double getLatitude()  { return latitude; }
    public double getLongitude() { return longitude; }

    /**
     * Returns a PostGIS-compatible point string {@code "POINT(lon lat)"}.
     * Longitude comes first per the WKT standard.
     */
    public String toWkt() {
        return "POINT(" + longitude + " " + latitude + ")";
    }

    /**
     * Returns a comma-separated string {@code "lat,lon"} for display purposes.
     */
    public String toLatLonString() {
        return latitude + "," + longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoCoordinates other)) return false;
        return Double.compare(latitude, other.latitude) == 0
                && Double.compare(longitude, other.longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }

    @Override
    public String toString() {
        return "GeoCoordinates{lat=" + latitude + ", lon=" + longitude + "}";
    }
}
