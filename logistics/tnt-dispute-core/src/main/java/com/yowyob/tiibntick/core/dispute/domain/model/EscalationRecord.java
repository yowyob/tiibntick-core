package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value Object recording a single escalation event in a dispute's history.
 * Immutable audit trail entry stored in {@link Dispute#escalationHistory}.
 *
 * @author MANFOUO Braun
 */
public final class EscalationRecord {

    private final LocalDateTime escalatedAt;
    private final String escalatedBy;
    private final String reason;
    private final DisputeStatus fromStatus;
    private final DisputeStatus toStatus;
    private final String assignedTo;

    private EscalationRecord(
            final LocalDateTime escalatedAt,
            final String escalatedBy,
            final String reason,
            final DisputeStatus fromStatus,
            final DisputeStatus toStatus,
            final String assignedTo) {
        this.escalatedAt = Objects.requireNonNull(escalatedAt);
        this.escalatedBy = Objects.requireNonNull(escalatedBy);
        this.reason = reason;
        this.fromStatus = Objects.requireNonNull(fromStatus);
        this.toStatus = Objects.requireNonNull(toStatus);
        this.assignedTo = assignedTo;
    }

    public static EscalationRecord of(
            final String escalatedBy,
            final String reason,
            final DisputeStatus fromStatus,
            final DisputeStatus toStatus,
            final String assignedTo) {
        return new EscalationRecord(LocalDateTime.now(), escalatedBy, reason, fromStatus, toStatus, assignedTo);
    }

    public LocalDateTime getEscalatedAt() { return escalatedAt; }
    public String getEscalatedBy() { return escalatedBy; }
    public String getReason() { return reason; }
    public DisputeStatus getFromStatus() { return fromStatus; }
    public DisputeStatus getToStatus() { return toStatus; }
    public String getAssignedTo() { return assignedTo; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof EscalationRecord that)) return false;
        return escalatedAt.equals(that.escalatedAt)
                && escalatedBy.equals(that.escalatedBy)
                && fromStatus == that.fromStatus
                && toStatus == that.toStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(escalatedAt, escalatedBy, fromStatus, toStatus);
    }
}
