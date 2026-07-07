package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command to assign a mediator to an open dispute.
 *
 * @author MANFOUO Braun
 */
public record AssignMediatorCommand(
        DisputeId disputeId,
        String tenantId,
        String mediatorId,
        String assignedBy
) {
    public AssignMediatorCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(mediatorId, "mediatorId is required");
        Objects.requireNonNull(assignedBy, "assignedBy is required");
    }
}
