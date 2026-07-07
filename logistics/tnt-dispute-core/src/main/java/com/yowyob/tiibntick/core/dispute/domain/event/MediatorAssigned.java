package com.yowyob.tiibntick.core.dispute.domain.event;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain event emitted when a mediator is assigned to a dispute.
 * Consumed by tnt-notify-core to alert the mediator and parties.
 *
 * @author MANFOUO Braun
 */
public record MediatorAssigned(
        DisputeId disputeId,
        String tenantId,
        String mediatorId,
        LocalDateTime occurredAt
) {
    public MediatorAssigned {
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(mediatorId);
        Objects.requireNonNull(occurredAt);
    }
}
