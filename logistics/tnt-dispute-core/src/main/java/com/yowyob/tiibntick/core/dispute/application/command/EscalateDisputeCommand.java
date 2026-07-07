package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command to escalate a dispute to a higher authority (arbitration).
 *
 * @author MANFOUO Braun
 */
public record EscalateDisputeCommand(
        DisputeId disputeId,
        String tenantId,
        String escalatedBy,
        String reason,
        String assignedArbitratorId
) {
    public EscalateDisputeCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(escalatedBy, "escalatedBy is required");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Escalation reason must not be blank");
        }
    }
}
