package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.enums.EvidenceSubmitterType;
import com.yowyob.tiibntick.core.dispute.domain.enums.EvidenceType;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command to add a piece of evidence to an ongoing dispute.
 *
 * @author MANFOUO Braun
 */
public record AddEvidenceCommand(
        DisputeId disputeId,
        String tenantId,
        String submittedBy,
        EvidenceSubmitterType submitterType,
        EvidenceType evidenceType,
        String fileKey,
        String description
) {
    public AddEvidenceCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(submittedBy, "submittedBy is required");
        Objects.requireNonNull(submitterType, "submitterType is required");
        Objects.requireNonNull(evidenceType, "evidenceType is required");
    }
}
