package com.yowyob.tiibntick.core.geo.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted postal address, in the informal-African-city shape: a street and/or a landmark ("repère"),
 * a district ("quartier"), a city, a region, a country, optional postal code and GPS coordinates.
 *
 * <p>Generic by design: any product backend keeps its addresses here and links them to the owner of its choice
 * through {@link AddressLink}. Same validity rule as the {@code common.vo.Address} value object: a city, a country
 * and at least one of street / landmark.
 *
 * <p>{@code tenantId} is {@code null} for an address that predates tenant scoping; such an address is visible to
 * every tenant.
 */
public final class Address {

    private final UUID id;
    private final UUID tenantId;
    private final String street;
    private final String landmark;
    private final String district;
    private final String city;
    private final String region;
    private final String country;
    private final String postalCode;
    private final Double latitude;
    private final Double longitude;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Address(UUID id, UUID tenantId, String street, String landmark, String district, String city,
                    String region, String country, String postalCode, Double latitude, Double longitude,
                    Instant createdAt, Instant updatedAt, boolean strict) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = tenantId;
        this.street = blankToNull(street);
        this.landmark = blankToNull(landmark);
        this.district = blankToNull(district);
        this.city = requireText(city, "city");
        this.region = blankToNull(region);
        this.country = requireText(country, "country");
        this.postalCode = blankToNull(postalCode);
        if (strict && this.street == null && this.landmark == null) {
            throw new IllegalArgumentException("At least one of 'street' or 'landmark' is required");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be given together");
        }
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** @param id the address id, or {@code null} to have one generated (a migrated address keeps its id) */
    public static Address create(UUID id, UUID tenantId, String street, String landmark, String district, String city,
                                 String region, String country, String postalCode, Double latitude, Double longitude) {
        Instant now = Instant.now();
        return new Address(id != null ? id : UUID.randomUUID(), tenantId, street, landmark, district, city, region,
                country, postalCode, latitude, longitude, now, now, true);
    }

    public static Address rehydrate(UUID id, UUID tenantId, String street, String landmark, String district,
                                    String city, String region, String country, String postalCode, Double latitude,
                                    Double longitude, Instant createdAt, Instant updatedAt) {
        // Legacy rows may have neither street nor landmark: reading never rejects what is already stored.
        return new Address(id, tenantId, street, landmark, district, city, region, country, postalCode, latitude,
                longitude, createdAt, updatedAt, false);
    }

    /** A copy carrying the new content, with the same id, tenant and creation date. */
    public Address withContent(String street, String landmark, String district, String city, String region,
                               String country, String postalCode, Double latitude, Double longitude) {
        return new Address(id, tenantId, street, landmark, district, city, region, country, postalCode, latitude,
                longitude, createdAt, Instant.now(), true);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String requireText(String s, String field) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return s.trim();
    }

    public UUID id()           { return id; }
    public UUID tenantId()     { return tenantId; }
    public String street()     { return street; }
    public String landmark()   { return landmark; }
    public String district()   { return district; }
    public String city()       { return city; }
    public String region()     { return region; }
    public String country()    { return country; }
    public String postalCode() { return postalCode; }
    public Double latitude()   { return latitude; }
    public Double longitude()  { return longitude; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Address that && id.equals(that.id));
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
