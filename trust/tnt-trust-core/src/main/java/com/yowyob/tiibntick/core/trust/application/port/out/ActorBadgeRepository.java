package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ActorBadge;

/**
 * Outbound Port — {@code ActorBadgeRepository}.
 *
 * <p>Persistence contract for {@link ActorBadge} value objects.
 * Implemented by the R2DBC adapter targeting the
 * {@code tnt_trust.actor_badges} table in the {@code tnt_trust_db} database.
 *
 * <p>This repository is the local PostgreSQL cache for on-chain badge data.
 * It is updated asynchronously when {@code yow.trust.events.committed}
 * notifications arrive from {@code yow-trust-event}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface ActorBadgeRepository {

    /**
     * Saves or updates an {@link ActorBadge}.
     *
     * @param badge the badge to persist
     * @return a {@link Mono} emitting the saved badge
     */
    Mono<ActorBadge> save(ActorBadge badge);

    /**
     * Finds all active (non-revoked) badges for an actor.
     *
     * @param actorId  the actor's unique identifier
     * @param tenantId the tenant identifier
     * @return a {@link Flux} of active badges
     */
    Flux<ActorBadge> findByActorId(String actorId, String tenantId);

    /**
     * Finds a specific badge by actor and badge type.
     * Returns empty if the actor does not hold this badge type or if it was revoked.
     *
     * @param actorId   the actor's unique identifier
     * @param badgeType the badge type to look up
     * @param tenantId  the tenant identifier
     * @return a {@link Mono} emitting the badge, or empty if not found
     */
    Mono<ActorBadge> findByActorIdAndBadgeType(String actorId, String badgeType, String tenantId);

    /**
     * Updates the Fabric transaction hash for a badge after on-chain confirmation.
     * Called by {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     *
     * @param badgeId the badge identifier
     * @param txHash  the Fabric transaction hash
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> updateTxHash(String badgeId, String txHash);

    /**
     * Marks a badge as revoked.
     *
     * @param badgeId the badge identifier to revoke
     * @return a {@link Mono} completing when the revocation is persisted
     */
    Mono<Void> revokeByBadgeId(String badgeId);

    /**
     * Checks whether an actor holds a specific badge type (non-revoked).
     *
     * @param actorId   the actor's unique identifier
     * @param badgeType the badge type to check
     * @param tenantId  the tenant identifier
     * @return a {@link Mono} emitting {@code true} if the badge exists and is active
     */
    Mono<Boolean> existsByActorAndType(String actorId, String badgeType, String tenantId);
}
