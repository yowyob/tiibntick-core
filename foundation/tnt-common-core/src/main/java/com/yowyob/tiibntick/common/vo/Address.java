package com.yowyob.tiibntick.common.vo;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable value object representing a postal / physical address, adapted for
 * the informal African urban context where formal street numbering is rare.
 *
 * <p>The {@code landmark} field is the key differentiator: it encodes the local
 * reference system used in cities like Yaoundé or Douala (e.g., "Face Pharmacie de la Paix",
 * "Après le carrefour Bastos"). {@code coordinates} provides a GPS fallback
 * for cases where text-based geocoding is unreliable.
 *
 * <p>At least one of ({@code street}, {@code landmark}) must be provided.
 *
 * Author: MANFOUO Braun
 */
public final class Address {

    private final String street;
    private final String landmark;
    private final String quarter;
    private final String city;
    private final String region;
    private final String country;
    private final String postalCode;
    private final GeoCoordinates coordinates;

    private Address(Builder builder) {
        this.street      = builder.street;
        this.landmark    = builder.landmark;
        this.quarter     = builder.quarter;
        this.city        = Objects.requireNonNull(builder.city, "city is required");
        this.region      = builder.region;
        this.country     = Objects.requireNonNull(builder.country, "country is required");
        this.postalCode  = builder.postalCode;
        this.coordinates = builder.coordinates;

        if ((street == null || street.isBlank()) && (landmark == null || landmark.isBlank())) {
            throw new IllegalArgumentException("At least one of 'street' or 'landmark' is required");
        }
    }

    // ── Static factories ──────────────────────────────────────────────────

    /**
     * Creates an address from a landmark (common in informal African contexts).
     *
     * @param landmark human reference (e.g., "En face du marché central")
     * @param quarter  neighborhood or district
     * @param city     city name
     * @param country  ISO 3166-1 alpha-2 or full country name
     */
    public static Address ofLandmark(String landmark, String quarter, String city, String country) {
        return new Builder().landmark(landmark).quarter(quarter).city(city).country(country).build();
    }

    /**
     * Creates a formal street address.
     */
    public static Address ofStreet(String street, String city, String country) {
        return new Builder().street(street).city(city).country(country).build();
    }

    /**
     * Creates an address from GPS coordinates with a display city/country.
     */
    public static Address ofCoordinates(GeoCoordinates coordinates, String city, String country) {
        return new Builder().landmark("GPS").coordinates(coordinates).city(city).country(country).build();
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public Optional<String> getStreet()           { return Optional.ofNullable(street); }
    public Optional<String> getLandmark()         { return Optional.ofNullable(landmark); }
    public Optional<String> getQuarter()          { return Optional.ofNullable(quarter); }
    public String getCity()                       { return city; }
    public Optional<String> getRegion()           { return Optional.ofNullable(region); }
    public String getCountry()                    { return country; }
    public Optional<String> getPostalCode()       { return Optional.ofNullable(postalCode); }
    public Optional<GeoCoordinates> getCoordinates(){ return Optional.ofNullable(coordinates); }

    /**
     * Returns a single-line human-readable representation.
     * Example: "Face Marché Central, Mokolo, Yaoundé, Cameroun"
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank())    sb.append(street).append(", ");
        if (landmark != null && !landmark.isBlank()) sb.append(landmark).append(", ");
        if (quarter != null && !quarter.isBlank())   sb.append(quarter).append(", ");
        sb.append(city);
        if (region != null && !region.isBlank())    sb.append(", ").append(region);
        sb.append(", ").append(country);
        return sb.toString();
    }

    // ── Object contract ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address other)) return false;
        return Objects.equals(street, other.street)
                && Objects.equals(landmark, other.landmark)
                && Objects.equals(quarter, other.quarter)
                && Objects.equals(city, other.city)
                && Objects.equals(country, other.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, landmark, quarter, city, country);
    }

    @Override
    public String toString() {
        return "Address{" + toDisplayString() + "}";
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String street;
        private String landmark;
        private String quarter;
        private String city;
        private String region;
        private String country;
        private String postalCode;
        private GeoCoordinates coordinates;

        public Builder street(String street)            { this.street = street; return this; }
        public Builder landmark(String landmark)        { this.landmark = landmark; return this; }
        public Builder quarter(String quarter)          { this.quarter = quarter; return this; }
        public Builder city(String city)                { this.city = city; return this; }
        public Builder region(String region)            { this.region = region; return this; }
        public Builder country(String country)          { this.country = country; return this; }
        public Builder postalCode(String postalCode)    { this.postalCode = postalCode; return this; }
        public Builder coordinates(GeoCoordinates c)   { this.coordinates = c; return this; }
        public Address build()                          { return new Address(this); }
    }
}
