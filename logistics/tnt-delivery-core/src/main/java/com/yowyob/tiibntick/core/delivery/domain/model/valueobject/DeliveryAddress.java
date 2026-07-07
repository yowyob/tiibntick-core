package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

/**
 * Immutable delivery address combining structured fields with informal landmarks.
 * Designed for the Cameroonian/African context where formal addressing is incomplete
 * and local landmarks (quartier, reference point) are the primary navigation guide.
 *
 * @author MANFOUO Braun
 */
public record DeliveryAddress(
        String street,
        String landmark,
        String district,
        String city,
        String country,
        GeoCoordinates coordinates
) {

    /**
     * Creates a minimal address from city and landmark (common informal case).
     */
    public static DeliveryAddress informal(String landmark, String district,
                                            String city, GeoCoordinates coordinates) {
        return new DeliveryAddress(null, landmark, district, city, "CM", coordinates);
    }

    /**
     * Returns a human-readable, one-line representation used in notifications.
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) sb.append(street).append(", ");
        if (landmark != null && !landmark.isBlank()) sb.append(landmark).append(", ");
        if (district != null && !district.isBlank()) sb.append(district).append(", ");
        sb.append(city);
        return sb.toString();
    }
}
