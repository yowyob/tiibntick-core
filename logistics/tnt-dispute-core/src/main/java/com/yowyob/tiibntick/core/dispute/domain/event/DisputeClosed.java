package com.yowyob.tiibntick.core.dispute.domain.event;

import com.yowyob.tiibntick.core.dispute.domain.enums.ClosureType;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain event emitted when a dispute reaches a terminal closed state.
 * Consumed by tnt-delivery-core to release the DISPUTED package state,
 * and tnt-actor-core to update actor reputation scores.
 *
 * @author MANFOUO Braun
 */
public record DisputeClosed(
        DisputeId disputeId,
        String tenantId,
        ClosureType closureType,
        String summary,
        LocalDateTime occurredAt
) {
    public DisputeClosed {
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(closureType);
        Objects.requireNonNull(occurredAt);
    }
}
