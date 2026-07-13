package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.List;

/**
 * Value Object — geographic coverage zone of a provider listing.
 * @author MANFOUO Braun
 */
public record CoverageZone(
        List<String> cities,
        List<String> districts,
        Double radiusKm,
        Double centerLat,
        Double centerLng,
        String polygonWkt
) {
    /** Returns true if the given coordinates fall within the coverage zone radius. */
    public boolean contains(double lat, double lng) {
        if (centerLat == null || centerLng == null || radiusKm == null) return false;
        double dLat = Math.toRadians(lat - centerLat);
        double dLng = Math.toRadians(lng - centerLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(centerLat)) * Math.cos(Math.toRadians(lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = 6371 * c;
        return distanceKm <= radiusKm;
    }

    public boolean containsCity(String city) {
        return cities != null && cities.stream()
                .anyMatch(c -> c.equalsIgnoreCase(city));
    }
}
