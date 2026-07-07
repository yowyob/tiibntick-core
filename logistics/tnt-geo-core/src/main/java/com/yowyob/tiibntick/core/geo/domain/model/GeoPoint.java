package com.yowyob.tiibntick.core.geo.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.Objects;

/**
 * Immutable geographic coordinate value object.
 * Encapsulates a WGS-84 latitude/longitude pair and provides
 * Haversine distance computation between two points.
 *
 * Author: MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class GeoPoint {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final double latitude;
    private final double longitude;

    private GeoPoint(double latitude, double longitude) {
        validateLatitude(latitude);
        validateLongitude(longitude);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude);
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    /**
     * Computes the great-circle distance to another point using the Haversine formula.
     * Time complexity: O(1).
     *
     * @param other the target point, must not be null
     * @return distance in kilometers, always >= 0
     */
    public double haversineDistanceTo(GeoPoint other) {
        Objects.requireNonNull(other, "other GeoPoint must not be null");

        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);

        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);

        double a = sinDLat * sinDLat
                + Math.cos(Math.toRadians(this.latitude))
                * Math.cos(Math.toRadians(other.latitude))
                * sinDLon * sinDLon;

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Returns the bearing (azimuth) in degrees [0, 360) from this point to the other.
     */
    public double bearingTo(GeoPoint other) {
        Objects.requireNonNull(other, "other GeoPoint must not be null");
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }

    private static void validateLatitude(double lat) {
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be in [-90, 90], got: " + lat);
        }
    }

    private static void validateLongitude(double lon) {
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude must be in [-180, 180], got: " + lon);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoPoint g)) return false;
        return Double.compare(g.latitude, latitude) == 0
                && Double.compare(g.longitude, longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }

    @Override
    public String toString() {
        return "GeoPoint{lat=" + latitude + ", lng=" + longitude + "}";
    }

    /**
     * Serializes to WKT POINT format: "POINT(lng lat)" (PostGIS convention: X=lng, Y=lat).
     */
    public String toWkt() {
        return "POINT(" + longitude + " " + latitude + ")";
    }

    /**
     * Parses a WKT POINT string: "POINT(lng lat)".
     */
    public static GeoPoint fromWkt(String wkt) {
        Objects.requireNonNull(wkt, "WKT string must not be null");
        String trimmed = wkt.trim();
        if (!trimmed.toUpperCase().startsWith("POINT")) {
            throw new IllegalArgumentException("Invalid WKT POINT: " + wkt);
        }
        int start = trimmed.indexOf('(');
        int end = trimmed.indexOf(')');
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("Invalid WKT POINT format: " + wkt);
        }
        String[] parts = trimmed.substring(start + 1, end).trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid WKT POINT coordinates: " + wkt);
        }
        double lng = Double.parseDouble(parts[0]);
        double lat = Double.parseDouble(parts[1]);
        return GeoPoint.of(lat, lng);
    }
}
