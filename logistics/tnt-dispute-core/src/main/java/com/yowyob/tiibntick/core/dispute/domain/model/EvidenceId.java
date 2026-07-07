package com.yowyob.tiibntick.core.dispute.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing the unique identifier of a {@link DisputeEvidence}.
 *
 * @author MANFOUO Braun
 */
public final class EvidenceId {

    private final String value;

    private EvidenceId(final String value) {
        Objects.requireNonNull(value, "EvidenceId value must not be null");
        this.value = value;
    }

    public static EvidenceId generate() {
        return new EvidenceId(UUID.randomUUID().toString());
    }

    public static EvidenceId of(final String value) {
        return new EvidenceId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof EvidenceId that)) return false;
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
