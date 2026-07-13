package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.UUID;

/**
 * Value Object — Strongly-typed identifier for ServiceOffer.
 * @author MANFOUO Braun
 */
public record ServiceOfferId(UUID value) {

    public static ServiceOfferId generate() {
        return new ServiceOfferId(UUID.randomUUID());
    }

    public static ServiceOfferId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("ServiceOfferId value must not be null");
        return new ServiceOfferId(value);
    }

    public static ServiceOfferId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
