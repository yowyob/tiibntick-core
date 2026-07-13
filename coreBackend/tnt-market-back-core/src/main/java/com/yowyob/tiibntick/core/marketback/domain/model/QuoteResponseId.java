package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.UUID;

/**
 * Value Object — Strongly-typed identifier for QuoteResponse.
 * @author MANFOUO Braun
 */
public record QuoteResponseId(UUID value) {

    public static QuoteResponseId generate() {
        return new QuoteResponseId(UUID.randomUUID());
    }

    public static QuoteResponseId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("QuoteResponseId value must not be null");
        return new QuoteResponseId(value);
    }

    public static QuoteResponseId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
