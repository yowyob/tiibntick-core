package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

public record FreelancerDissociatedEvent(
        UUID eventId,
        UUID freelancerActorId,
        UUID tenantId,
        UUID agencyId,
        String reason,
        Instant occurredAt) {

    public static FreelancerDissociatedEvent of(UUID freelancerActorId, UUID tenantId,
                                                 UUID agencyId, String reason) {
        return new FreelancerDissociatedEvent(UUID.randomUUID(), freelancerActorId, tenantId,
                agencyId, reason, Instant.now());
    }
}
