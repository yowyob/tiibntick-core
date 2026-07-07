package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command to formally start the mediation phase of a dispute.
 *
 * @author MANFOUO Braun
 */
public record StartMediationCommand(
        DisputeId disputeId,
        String tenantId,
        String mediatorId
) {
    public StartMediationCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(mediatorId, "mediatorId is required");
    }
}
