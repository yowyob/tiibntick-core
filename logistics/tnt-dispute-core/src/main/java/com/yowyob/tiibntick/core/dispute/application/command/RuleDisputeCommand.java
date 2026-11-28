package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.enums.ResolutionType;
import com.yowyob.tiibntick.core.dispute.domain.model.CompensationDetails;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command for a mediator or arbitrator to issue a ruling on a dispute.
 *
 * @author MANFOUO Braun
 */
public record RuleDisputeCommand(
        DisputeId disputeId,
        String tenantId,
        String ruledBy,
        ResolutionType resolutionType,
        boolean compensationRequired,
        CompensationDetails compensation,
        String summary,
        /** Party found at fault (pays the return / the new delivery, [B.6]); {@code null} = not decided (lot L4). */
        com.yowyob.tiibntick.core.dispute.domain.enums.DisputeResponsibleParty responsibleParty
) {
    public RuleDisputeCommand(DisputeId disputeId, String tenantId, String ruledBy, ResolutionType resolutionType,
                              boolean compensationRequired, CompensationDetails compensation, String summary) {
        this(disputeId, tenantId, ruledBy, resolutionType, compensationRequired, compensation, summary, null);
    }

    public RuleDisputeCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(ruledBy, "ruledBy is required");
        Objects.requireNonNull(resolutionType, "resolutionType is required");
        if (compensationRequired && compensation == null) {
            throw new IllegalArgumentException("Compensation details are required when compensationRequired is true");
        }
    }
}
