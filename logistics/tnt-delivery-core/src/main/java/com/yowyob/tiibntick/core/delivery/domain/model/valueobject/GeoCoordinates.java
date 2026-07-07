package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;

/**
 * Immutable geographic coordinates (WGS-84 decimal degrees).
 * Used for pickup/dropoff locations and real-time driver position tracking.
 *
 * @author MANFOUO Braun
 */
public record GeoCoordinates(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public GeoCoordinates {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new DeliveryDomainException(
                "Latitude must be between -90 and 90, got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new DeliveryDomainException(
                "Longitude must be between -180 and 180, got: " + longitude);
        }
    }

    /**
     * Computes the Haversine great-circle distance (km) between this point and the target.
     * Used as admissible heuristic in A* routing and as ETA base distance.
     *
     * @param target destination coordinates
     * @return distance in kilometres
     */
    public double haversineDistanceTo(GeoCoordinates target) {
        double dLat = Math.toRadians(target.latitude() - this.latitude);
        double dLon = Math.toRadians(target.longitude() - this.longitude);
        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double a = sinDLat * sinDLat
                + Math.cos(Math.toRadians(this.latitude))
                    * Math.cos(Math.toRadians(target.latitude()))
                    * sinDLon * sinDLon;
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    @Override
    public String toString() {
        return "(" + latitude + ", " + longitude + ")";
    }
}
