package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import java.security.SecureRandom;

/**
 * Unique immutable tracking code identifying a parcel across the entire TiiBnTick platform (D-136: carried by the
 * {@code Parcel}, kept across its outbound, return and redelivery deliveries).
 *
 * <p>Format: {@code TNT-YYYYMMDD-XXXXXXXX} where:
 * <ul>
 *   <li>{@code TNT} — TiiBnTick prefix</li>
 *   <li>{@code YYYYMMDD} — creation date</li>
 *   <li>{@code XXXXXXXX} — 8 alphanumeric random chars (uppercase)</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record TrackingCode(String value) {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** D-137: codes are drawn from a cryptographically strong generator (they open the public tracking page). */
    private static final SecureRandom RANDOM = new SecureRandom();

    public TrackingCode {
        if (value == null || !value.matches("TNT-\\d{8}-[A-Z0-9]{8}")) {
            throw new IllegalArgumentException("Invalid tracking code format: " + value);
        }
    }

    /**
     * Generates a new unique tracking code based on the current UTC date.
     */
    public static TrackingCode generate() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        String datePart = String.format("%04d%02d%02d",
                today.getYear(), today.getMonthValue(), today.getDayOfMonth());
        StringBuilder random = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            random.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return new TrackingCode("TNT-" + datePart + "-" + random);
    }

    @Override
    public String toString() {
        return value;
    }
}
