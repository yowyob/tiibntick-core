package com.yowyob.tiibntick.core.dispute.domain.event;

import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain event emitted when a dispute is escalated to a higher authority.
 * Consumed by tnt-notify-core and tnt-administration-core.
 *
 * @author MANFOUO Braun
 */
public record DisputeEscalated(
        DisputeId disputeId,
        String tenantId,
        DisputeStatus fromStatus,
        String reason,
        String escalatedBy,
        LocalDateTime occurredAt
) {
    public DisputeEscalated {
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(fromStatus);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(escalatedBy);
        Objects.requireNonNull(occurredAt);
    }
}
