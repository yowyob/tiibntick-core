package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.UUID;

/**
 * Value Object — Strongly-typed identifier for QuoteRequest.
 * @author MANFOUO Braun
 */
public record QuoteRequestId(UUID value) {

    public static QuoteRequestId generate() {
        return new QuoteRequestId(UUID.randomUUID());
    }

    public static QuoteRequestId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("QuoteRequestId value must not be null");
        return new QuoteRequestId(value);
    }

    public static QuoteRequestId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
