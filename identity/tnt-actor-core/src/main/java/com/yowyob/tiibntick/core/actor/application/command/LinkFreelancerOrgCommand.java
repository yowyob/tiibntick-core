package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.FreelancerRole;

import java.util.UUID;

/**
 * Command to link a FreelancerProfile actor to a FreelancerOrganization.
 *
 * <p>This command is used by two flows:
 * <ol>
 *   <li><strong>OWNER creation</strong>: when a freelancer registers a new
 *       FreelancerOrganization in {@code tnt-organization-core}, the
 *       {@code FreelancerOrgEventConsumer} consumes the
 *       {@code tnt.freelancer_org.created} event and issues this command
 *       with {@link FreelancerRole#OWNER}.</li>
 *   <li><strong>SUB_DELIVERER acceptance</strong>: when a sub-deliverer
 *       accepts an invitation, the {@code FreelancerOrgEventConsumer}
 *       consumes {@code tnt.freelancer_org.sub_deliverer.associated} and
 *       issues this command with {@link FreelancerRole#SUB_DELIVERER}.</li>
 * </ol>
 *
 * @param tenantId        multi-tenant key of the freelancer
 * @param actorId         actor UUID of the freelancer to link
 * @param freelancerOrgId UUID of the FreelancerOrganization
 * @param role            role within the org (OWNER or SUB_DELIVERER)
 * @param isOrgVerified   initial org verification status
 *
 * @author MANFOUO Braun
 */
public record LinkFreelancerOrgCommand(
        UUID tenantId,
        UUID actorId,
        UUID freelancerOrgId,
        FreelancerRole role,
        boolean isOrgVerified) {
}
