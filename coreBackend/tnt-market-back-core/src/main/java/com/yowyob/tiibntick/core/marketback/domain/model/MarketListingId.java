package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.UUID;

/**
 * Value Object — Strongly-typed identifier for MarketListing.
 * @author MANFOUO Braun
 */
public record MarketListingId(UUID value) {

    public static MarketListingId generate() {
        return new MarketListingId(UUID.randomUUID());
    }

    public static MarketListingId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("MarketListingId value must not be null");
        return new MarketListingId(value);
    }

    public static MarketListingId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
