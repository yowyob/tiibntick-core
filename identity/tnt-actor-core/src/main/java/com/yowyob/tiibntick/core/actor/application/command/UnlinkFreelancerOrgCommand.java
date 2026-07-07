package com.yowyob.tiibntick.core.actor.application.command;

import java.util.UUID;

/**
 * Command to remove a FreelancerProfile's link to a FreelancerOrganization.
 *
 * <p>Issued by {@code FreelancerOrgEventConsumer} when
 * {@code tnt.freelancer_org.sub_deliverer.revoked} is received for a
 * sub-deliverer, or when an org is dissolved.
 *
 * @param tenantId        multi-tenant key of the freelancer
 * @param actorId         actor UUID of the freelancer to unlink
 * @param freelancerOrgId UUID of the FreelancerOrganization being unlinked
 *
 * @author MANFOUO Braun
 */
public record UnlinkFreelancerOrgCommand(
        UUID tenantId,
        UUID actorId,
        UUID freelancerOrgId) {
}
