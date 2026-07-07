package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.enums.ClosureType;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command to close a dispute with a specific closure type and summary.
 *
 * @author MANFOUO Braun
 */
public record CloseDisputeCommand(
        DisputeId disputeId,
        String tenantId,
        String closedBy,
        ClosureType closureType,
        String summary
) {
    public CloseDisputeCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(closedBy, "closedBy is required");
        Objects.requireNonNull(closureType, "closureType is required");
    }
}
