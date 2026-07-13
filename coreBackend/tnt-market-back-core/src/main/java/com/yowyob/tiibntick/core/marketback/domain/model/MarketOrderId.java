package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.UUID;

/**
 * Value Object — Strongly-typed identifier for MarketOrder.
 * @author MANFOUO Braun
 */
public record MarketOrderId(UUID value) {

    public static MarketOrderId generate() {
        return new MarketOrderId(UUID.randomUUID());
    }

    public static MarketOrderId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("MarketOrderId value must not be null");
        return new MarketOrderId(value);
    }

    public static MarketOrderId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
