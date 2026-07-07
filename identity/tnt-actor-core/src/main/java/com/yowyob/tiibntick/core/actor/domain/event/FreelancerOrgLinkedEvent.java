package com.yowyob.tiibntick.core.actor.domain.event;

import com.yowyob.tiibntick.core.actor.domain.model.FreelancerRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted by {@code tnt-actor-core} when a FreelancerProfile
 * is successfully linked to a FreelancerOrganization.
 *
 * <p>Published after the actor's {@code freelancerOrgId} and {@code roleInOrg}
 * fields are persisted. Consumers may use this for dashboard updates,
 * notifications, and Blockchain DID enrichment.
 *
 * @param eventId         unique event identifier
 * @param actorId         the freelancer actor UUID
 * @param tenantId        multi-tenant key
 * @param freelancerOrgId UUID of the linked FreelancerOrganization
 * @param roleInOrg       the actor's role within the org
 * @param occurredAt      event timestamp (UTC)
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgLinkedEvent(
        UUID eventId,
        UUID actorId,
        UUID tenantId,
        UUID freelancerOrgId,
        FreelancerRole roleInOrg,
        Instant occurredAt) {

    public static FreelancerOrgLinkedEvent of(UUID actorId, UUID tenantId,
                                               UUID freelancerOrgId,
                                               FreelancerRole roleInOrg) {
        return new FreelancerOrgLinkedEvent(
                UUID.randomUUID(), actorId, tenantId,
                freelancerOrgId, roleInOrg, Instant.now());
    }
}
