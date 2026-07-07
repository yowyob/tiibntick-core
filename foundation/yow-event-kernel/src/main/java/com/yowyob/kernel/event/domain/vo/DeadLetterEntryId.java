package com.yowyob.kernel.event.domain.vo;

import java.util.UUID;

/** Strongly-typed identifier for a {@link yowyob.kernel.event.domain.model.DeadLetterEntry}. */
public record DeadLetterEntryId(String value) {

    public static DeadLetterEntryId generate() {
        return new DeadLetterEntryId(UUID.randomUUID().toString());
    }

    public static DeadLetterEntryId of(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DeadLetterEntryId must not be blank");
        }
        return new DeadLetterEntryId(value);
    }
}
