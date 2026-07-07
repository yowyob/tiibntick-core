package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command to mark compensation as paid and close the dispute.
 *
 * @author MANFOUO Braun
 */
public record ProcessCompensationCommand(
        DisputeId disputeId,
        String tenantId,
        String paymentReference,
        String processedBy
) {
    public ProcessCompensationCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(paymentReference, "paymentReference is required");
        Objects.requireNonNull(processedBy, "processedBy is required");
    }
}
