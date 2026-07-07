package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.FreelancerRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command to assign a FreelancerOrganization to execute a delivery.
 *
 * <p>Triggered when a client selects a FreelancerOrg's response to their delivery announcement
 * via {@code SelectAnnouncementResponseCommand} where the responder is a FreelancerOrg.
 *
 * @author MANFOUO Braun
 */
public record AssignFreelancerOrgCommand(
        /** The delivery to assign. */
        @NotNull UUID deliveryId,

        /** The tenant scope. */
        @NotNull UUID tenantId,

        /**
         * The FreelancerOrganization UUID executing this delivery.
         * References tnt-organization-core UUID — pure integration key.
         */
        @NotBlank String freelancerOrgId,

        /**
         * The FreelancerOrg member's role in this delivery.
         * OWNER = executing directly. SUB_DELIVERER = partner executing on behalf.
         */
        @NotNull FreelancerRole freelancerRole
) {}
