package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

public record FreelancerAssociatedEvent(
        UUID eventId,
        UUID freelancerActorId,
        UUID tenantId,
        UUID agencyId,
        Instant occurredAt) {

    public static FreelancerAssociatedEvent of(UUID freelancerActorId, UUID tenantId, UUID agencyId) {
        return new FreelancerAssociatedEvent(UUID.randomUUID(), freelancerActorId, tenantId,
                agencyId, Instant.now());
    }
}
