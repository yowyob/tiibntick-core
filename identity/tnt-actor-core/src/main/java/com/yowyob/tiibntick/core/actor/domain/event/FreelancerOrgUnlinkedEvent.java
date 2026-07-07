package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted by {@code tnt-actor-core} when a FreelancerProfile's
 * FreelancerOrganization link is removed.
 *
 * <p>Triggered either by the OWNER dissolving the org or a sub-deliverer
 * being revoked/leaving.
 *
 * @param eventId         unique event identifier
 * @param actorId         the freelancer actor UUID
 * @param tenantId        multi-tenant key
 * @param freelancerOrgId UUID of the previously linked FreelancerOrganization
 * @param occurredAt      event timestamp (UTC)
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgUnlinkedEvent(
        UUID eventId,
        UUID actorId,
        UUID tenantId,
        UUID freelancerOrgId,
        Instant occurredAt) {

    public static FreelancerOrgUnlinkedEvent of(UUID actorId, UUID tenantId,
                                                 UUID freelancerOrgId) {
        return new FreelancerOrgUnlinkedEvent(
                UUID.randomUUID(), actorId, tenantId, freelancerOrgId, Instant.now());
    }
}
