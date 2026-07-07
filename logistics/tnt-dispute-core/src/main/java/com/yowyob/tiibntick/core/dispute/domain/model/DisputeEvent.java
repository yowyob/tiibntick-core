package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.domain.enums.ActorType;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeEventType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Entity representing an immutable audit-trail event on a dispute's timeline.
 *
 * <p>Each action taken on a {@link Dispute} (mediator assignment, evidence submission,
 * ruling, escalation, etc.) is recorded as a {@code DisputeEvent}. Together they
 * form a complete, tamper-resistant chronological log.
 *
 * @author MANFOUO Braun
 */
public final class DisputeEvent {

    private final DisputeEventId id;
    private final DisputeId disputeId;
    private final DisputeEventType type;
    private final String description;
    private final String performedBy;
    private final ActorType performedByType;
    private final LocalDateTime occurredAt;
    private final Map<String, String> metadata;

    private DisputeEvent(
            final DisputeEventId id,
            final DisputeId disputeId,
            final DisputeEventType type,
            final String description,
            final String performedBy,
            final ActorType performedByType,
            final LocalDateTime occurredAt,
            final Map<String, String> metadata) {
        this.id = Objects.requireNonNull(id);
        this.disputeId = Objects.requireNonNull(disputeId);
        this.type = Objects.requireNonNull(type);
        this.description = description;
        this.performedBy = performedBy;
        this.performedByType = Objects.requireNonNull(performedByType);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    public static DisputeEvent of(
            final DisputeId disputeId,
            final DisputeEventType type,
            final String description,
            final String performedBy,
            final ActorType performedByType) {
        return new DisputeEvent(
                DisputeEventId.generate(), disputeId, type, description,
                performedBy, performedByType, LocalDateTime.now(), null);
    }

    public static DisputeEvent withMetadata(
            final DisputeId disputeId,
            final DisputeEventType type,
            final String description,
            final String performedBy,
            final ActorType performedByType,
            final Map<String, String> metadata) {
        return new DisputeEvent(
                DisputeEventId.generate(), disputeId, type, description,
                performedBy, performedByType, LocalDateTime.now(), metadata);
    }

    public static DisputeEvent reconstitute(
            final DisputeEventId id,
            final DisputeId disputeId,
            final DisputeEventType type,
            final String description,
            final String performedBy,
            final ActorType performedByType,
            final LocalDateTime occurredAt,
            final Map<String, String> metadata) {
        return new DisputeEvent(id, disputeId, type, description, performedBy, performedByType, occurredAt, metadata);
    }

    public DisputeEventId getId() { return id; }
    public DisputeId getDisputeId() { return disputeId; }
    public DisputeEventType getType() { return type; }
    public String getDescription() { return description; }
    public String getPerformedBy() { return performedBy; }
    public ActorType getPerformedByType() { return performedByType; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeEvent that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
