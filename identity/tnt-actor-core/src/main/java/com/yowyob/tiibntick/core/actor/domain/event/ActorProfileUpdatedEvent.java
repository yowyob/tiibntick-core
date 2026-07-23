package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic domain event — emitted whenever an actor profile (Client, Deliverer, Freelancer,
 * RelayOperator) is mutated, regardless of which specific field changed. Consumed by
 * tnt-sync-core for delta-pull indexing; dedicated events ({@link KycValidatedEvent},
 * {@link BadgeEarnedEvent}, ...) remain the source of truth for reacting to a specific
 * change — this one only signals "something on this profile changed".
 *
 * @author MANFOUO Braun
 */
public record ActorProfileUpdatedEvent(
        UUID eventId,
        UUID actorId,
        UUID tenantId,
        String actorType,
        String updateReason,
        Instant occurredAt) {

    public static ActorProfileUpdatedEvent of(UUID actorId, UUID tenantId, String actorType,
                                               String updateReason) {
        return new ActorProfileUpdatedEvent(UUID.randomUUID(), actorId, tenantId, actorType,
                updateReason, Instant.now());
    }
}
