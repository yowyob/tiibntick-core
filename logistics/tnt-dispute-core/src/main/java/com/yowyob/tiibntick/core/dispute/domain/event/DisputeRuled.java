package com.yowyob.tiibntick.core.dispute.domain.event;

import com.yowyob.tiibntick.core.dispute.domain.enums.ResolutionType;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain event emitted when a mediator or arbitrator issues a ruling.
 * If compensation is required, triggers tnt-billing-wallet.
 *
 * @author MANFOUO Braun
 */
public record DisputeRuled(
        DisputeId disputeId,
        String tenantId,
        ResolutionType resolutionType,
        boolean compensationRequired,
        String mediatorId,
        LocalDateTime occurredAt
) {
    public DisputeRuled {
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resolutionType);
        Objects.requireNonNull(mediatorId);
        Objects.requireNonNull(occurredAt);
    }
}
