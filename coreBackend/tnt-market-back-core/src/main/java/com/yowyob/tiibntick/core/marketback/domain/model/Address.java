package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Value Object — postal/geo address.
 * @author MANFOUO Braun
 */
public record Address(
        String street,
        String district,
        String city,
        String country,
        String postalCode,
        Double lat,
        Double lng,
        String landmark
) {
    public String formatted() {
        return String.format("%s, %s, %s, %s", street, district, city, country);
    }

    public boolean hasCoordinates() {
        return lat != null && lng != null;
    }
}
