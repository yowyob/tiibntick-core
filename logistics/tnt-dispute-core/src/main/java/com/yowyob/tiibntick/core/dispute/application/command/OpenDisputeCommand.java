package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.enums.*;

import java.util.Objects;

/**
 * Command to open a new dispute. Validated before reaching the domain layer.
 *
 * @author MANFOUO Braun
 */
public record OpenDisputeCommand(
        String tenantId,
        String claimantId,
        ClaimantType claimantType,
        String respondentId,
        RespondentType respondentType,
        DisputeCause cause,
        DisputeCategory category,
        DisputePriority priority,
        String missionId,
        String packageId,
        String trackingCode,
        String description,

        // ── : FreelancerOrg context ──────────────────────────────────────
        /**
         * UUID of the respondent org (FreelancerOrg or Agency).
         * Required when respondentType is FREELANCER_ORG, AGENCY, HUB_POINT, or LINK_NETWORK.
         * References tnt-organization-core UUID — pure integration key.
         */
        String respondentOrgId,

        /**
         * UUID of the sub-deliverer implicated in the dispute.
         * Null when the FreelancerOrg OWNER executed the mission directly.
         */
        String impliedSubDelivererId,

        /**
         * Whether a SUB_DELIVERER is involved in this dispute.
         */
        Boolean subDelivererInvolved
) {
    public OpenDisputeCommand {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(claimantId, "claimantId is required");
        Objects.requireNonNull(claimantType, "claimantType is required");
        Objects.requireNonNull(respondentId, "respondentId is required");
        Objects.requireNonNull(respondentType, "respondentType is required");
        Objects.requireNonNull(cause, "cause is required");
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(priority, "priority is required");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }
}
