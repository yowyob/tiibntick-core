package com.yowyob.kernel.event.domain.vo;

import java.util.UUID;

/** Strongly-typed identifier for an {@link yowyob.kernel.event.domain.model.EventSchema}. */
public record SchemaId(String value) {

    public static SchemaId generate() {
        return new SchemaId(UUID.randomUUID().toString());
    }

    public static SchemaId of(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SchemaId must not be blank");
        }
        return new SchemaId(value);
    }
}
