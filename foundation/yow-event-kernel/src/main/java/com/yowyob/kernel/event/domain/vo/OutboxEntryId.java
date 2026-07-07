package com.yowyob.kernel.event.domain.vo;

import java.util.UUID;

/** Strongly-typed identifier for a {@link yowyob.kernel.event.domain.model.OutboxEntry}. */
public record OutboxEntryId(String value) {

    public static OutboxEntryId generate() {
        return new OutboxEntryId(UUID.randomUUID().toString());
    }

    public static OutboxEntryId of(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OutboxEntryId must not be blank");
        }
        return new OutboxEntryId(value);
    }
}
