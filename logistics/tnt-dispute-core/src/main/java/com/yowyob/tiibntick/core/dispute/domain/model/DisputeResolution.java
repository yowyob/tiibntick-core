package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.domain.enums.ResolutionType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value Object representing the formal ruling issued on a dispute.
 * Immutable once set on the {@link Dispute} aggregate.
 *
 * @author MANFOUO Braun
 */
public final class DisputeResolution {

    private final ResolutionType type;
    private final boolean compensationRequired;
    private final String mediatorId;
    private final String summary;
    private final LocalDateTime occurredAt;

    private DisputeResolution(
            final ResolutionType type,
            final boolean compensationRequired,
            final String mediatorId,
            final String summary,
            final LocalDateTime occurredAt) {
        this.type = Objects.requireNonNull(type, "ResolutionType must not be null");
        this.compensationRequired = compensationRequired;
        this.mediatorId = mediatorId;
        this.summary = summary;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static DisputeResolution of(
            final ResolutionType type,
            final boolean compensationRequired,
            final String mediatorId,
            final String summary) {
        return new DisputeResolution(type, compensationRequired, mediatorId, summary, LocalDateTime.now());
    }

    public ResolutionType getType() { return type; }
    public boolean isCompensationRequired() { return compensationRequired; }
    public String getMediatorId() { return mediatorId; }
    public String getSummary() { return summary; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeResolution that)) return false;
        return compensationRequired == that.compensationRequired
                && type == that.type
                && Objects.equals(mediatorId, that.mediatorId)
                && Objects.equals(summary, that.summary)
                && occurredAt.equals(that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, compensationRequired, mediatorId, summary, occurredAt);
    }

    @Override
    public String toString() {
        return "DisputeResolution{type=%s, compensation=%b, mediator=%s}".formatted(type, compensationRequired, mediatorId);
    }
}
