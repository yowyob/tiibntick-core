package com.yowyob.tiibntick.core.sales.domain.model;

import java.util.Objects;

/**
 * Address value object adapted to the Cameroonian informal logistics context.
 * Supports quartier (neighborhood), landmark, and optional GPS coordinates.
 * Author: MANFOUO Braun
 */
public record TntAddress(
        String street,
        String quartier,
        String city,
        String country,
        String landmark,
        Double latitude,
        Double longitude,
        String recipientName,
        String recipientPhone
) {

    public TntAddress {
        Objects.requireNonNull(city, "city is required");
        Objects.requireNonNull(country, "country is required");
    }

    public static TntAddress of(String quartier, String city, String country,
                                 String landmark, String recipientName, String recipientPhone) {
        return new TntAddress(null, quartier, city, country, landmark, null, null, recipientName, recipientPhone);
    }

    public static TntAddress withGps(String quartier, String city, String country,
                                      String landmark, double lat, double lon,
                                      String recipientName, String recipientPhone) {
        return new TntAddress(null, quartier, city, country, landmark, lat, lon, recipientName, recipientPhone);
    }

    public boolean hasGpsCoordinates() {
        return latitude != null && longitude != null;
    }

    public String displayLabel() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) sb.append(street).append(", ");
        if (quartier != null && !quartier.isBlank()) sb.append(quartier).append(", ");
        sb.append(city);
        if (landmark != null && !landmark.isBlank()) sb.append(" (Ref: ").append(landmark).append(")");
        return sb.toString();
    }
}
