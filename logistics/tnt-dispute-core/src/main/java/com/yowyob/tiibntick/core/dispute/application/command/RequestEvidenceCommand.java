package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Command to request additional evidence from a party in a dispute under investigation.
 *
 * @author MANFOUO Braun
 */
public record RequestEvidenceCommand(
        DisputeId disputeId,
        String tenantId,
        String requestedFrom,
        String requestedBy,
        LocalDateTime deadline,
        String reason
) {
    public RequestEvidenceCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(requestedFrom, "requestedFrom is required");
        Objects.requireNonNull(deadline, "deadline is required");
        if (deadline.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Evidence deadline must be in the future");
        }
    }
}
