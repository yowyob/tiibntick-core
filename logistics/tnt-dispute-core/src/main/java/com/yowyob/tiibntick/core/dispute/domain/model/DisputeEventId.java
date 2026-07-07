package com.yowyob.tiibntick.core.dispute.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing the unique identifier of a {@link DisputeEvent}.
 *
 * @author MANFOUO Braun
 */
public final class DisputeEventId {

    private final String value;

    private DisputeEventId(final String value) {
        this.value = Objects.requireNonNull(value);
    }

    public static DisputeEventId generate() {
        return new DisputeEventId(UUID.randomUUID().toString());
    }

    public static DisputeEventId of(final String value) {
        return new DisputeEventId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeEventId that)) return false;
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
