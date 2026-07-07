package com.yowyob.kernel.event.domain.vo;

import java.util.UUID;

/**
 * Strongly-typed identifier for a {@link yowyob.kernel.event.domain.model.DomainEventEnvelope}.
 *
 * <p>Using a dedicated type instead of a raw {@code String} or {@code UUID}
 * prevents accidental confusion with other identifiers (e.g. aggregate IDs).
 *
 * @param value the underlying UUID string representation
 */
public record EnvelopeId(String value) {

    /**
     * Creates a new, globally unique {@code EnvelopeId}.
     *
     * @return a freshly generated envelope identifier
     */
    public static EnvelopeId generate() {
        return new EnvelopeId(UUID.randomUUID().toString());
    }

    /**
     * Wraps an existing raw value into a typed identifier.
     *
     * @param value the raw UUID string — must not be {@code null} or blank
     * @return the typed identifier
     * @throws IllegalArgumentException if the value is blank
     */
    public static EnvelopeId of(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("EnvelopeId value must not be blank");
        }
        return new EnvelopeId(value);
    }
}
