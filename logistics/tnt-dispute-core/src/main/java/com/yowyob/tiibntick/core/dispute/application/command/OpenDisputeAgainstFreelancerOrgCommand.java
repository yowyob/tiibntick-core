package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.enums.*;

import java.util.Objects;

/**
 * Command to open a dispute specifically targeting a FreelancerOrganization.
 *
 * <p>A convenience command that pre-sets the respondentType to FREELANCER_ORG
 * and ensures the respondentOrgId is populated. Converted to an {@link OpenDisputeCommand}
 * internally by {@code DisputeCommandService}.
 *
 * @author MANFOUO Braun
 */
public record OpenDisputeAgainstFreelancerOrgCommand(
        String tenantId,
        String claimantId,
        ClaimantType claimantType,

        /**
         * The FreelancerOrg being disputed.
         * References tnt-organization-core UUID — pure integration key.
         */
        String freelancerOrgId,

        /**
         * The OWNER actor UUID of the FreelancerOrg (the primary respondent person).
         * This is the individual actor to notify; the org is the legal entity.
         */
        String freelancerOrgOwnerId,

        /**
         * UUID of the SUB_DELIVERER who executed the disputed delivery.
         * Null if the OWNER executed directly.
         */
        String impliedSubDelivererId,

        DisputeCause cause,
        DisputeCategory category,
        DisputePriority priority,
        String missionId,
        String packageId,
        String trackingCode,
        String description
) {
    public OpenDisputeAgainstFreelancerOrgCommand {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(claimantId, "claimantId is required");
        Objects.requireNonNull(claimantType, "claimantType is required");
        Objects.requireNonNull(freelancerOrgId, "freelancerOrgId is required");
        Objects.requireNonNull(freelancerOrgOwnerId, "freelancerOrgOwnerId is required");
        Objects.requireNonNull(cause, "cause is required");
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(priority, "priority is required");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    /**
     * Converts this command to a standard {@link OpenDisputeCommand} with
     * FREELANCER_ORG respondent type and FreelancerOrg context pre-filled.
     */
    public OpenDisputeCommand toOpenDisputeCommand() {
        return new OpenDisputeCommand(
                tenantId, claimantId, claimantType,
                freelancerOrgOwnerId, RespondentType.FREELANCER_ORG,
                cause, category, priority,
                missionId, packageId, trackingCode, description,
                freelancerOrgId, impliedSubDelivererId,
                impliedSubDelivererId != null
        );
    }
}
