package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command for a claimant to voluntarily withdraw their dispute.
 *
 * @author MANFOUO Braun
 */
public record WithdrawDisputeCommand(
        DisputeId disputeId,
        String tenantId,
        String claimantId
) {
    public WithdrawDisputeCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(claimantId, "claimantId is required");
    }
}
