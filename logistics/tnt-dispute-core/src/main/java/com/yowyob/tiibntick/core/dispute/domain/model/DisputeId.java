package com.yowyob.tiibntick.core.dispute.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing the unique identifier of a {@link Dispute}.
 * Wraps a UUID to provide type safety and prevent primitive obsession.
 *
 * @author MANFOUO Braun
 */
public final class DisputeId {

    private final String value;

    private DisputeId(final String value) {
        Objects.requireNonNull(value, "DisputeId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("DisputeId value must not be blank");
        }
        this.value = value;
    }

    /**
     * Generates a new, random {@code DisputeId}.
     *
     * @return a new {@code DisputeId} backed by a random UUID
     */
    public static DisputeId generate() {
        return new DisputeId(UUID.randomUUID().toString());
    }

    /**
     * Reconstructs a {@code DisputeId} from a previously persisted string value.
     *
     * @param value the string representation of the UUID
     * @return the corresponding {@code DisputeId}
     * @throws IllegalArgumentException if the value is null or blank
     */
    public static DisputeId of(final String value) {
        return new DisputeId(value);
    }

    /**
     * Returns the raw string value of this identifier.
     *
     * @return the UUID string
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
