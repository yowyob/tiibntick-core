package com.yowyob.tiibntick.core.dispute.domain.event;

import com.yowyob.tiibntick.core.dispute.domain.enums.EvidenceType;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain event emitted when evidence is submitted to a dispute.
 * Consumed by tnt-trust to optionally anchor the evidence on the blockchain.
 *
 * @author MANFOUO Braun
 */
public record EvidenceSubmitted(
        DisputeId disputeId,
        String tenantId,
        String evidenceId,
        EvidenceType evidenceType,
        String submittedBy,
        LocalDateTime occurredAt
) {
    public EvidenceSubmitted {
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(evidenceId);
        Objects.requireNonNull(evidenceType);
        Objects.requireNonNull(submittedBy);
        Objects.requireNonNull(occurredAt);
    }
}
