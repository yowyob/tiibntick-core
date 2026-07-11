package com.yowyob.tiibntick.core.actor.application.port.out;

import java.util.UUID;

/**
 * Actor-owned payload for {@link IBadgeAnchorPort#anchor}.
 *
 * <p>Deliberately independent from any {@code tnt-trust-core} domain type — the
 * implementing adapter (in {@code tnt-trust-core}) maps this into a
 * {@code LogisticTrustEvent}, keeping the hexagonal boundary between the two modules.
 *
 * <p><strong>Known gap:</strong> {@code tnt-trust-core}'s underlying
 * {@code RecordBadgeUseCase} also accepts a reputation-points value, but
 * {@code tnt-actor-core}'s {@code Badge} domain model doesn't track points —
 * the adapter passes {@code 0}. Extend {@code Badge}/{@code EarnBadgeCommand}
 * with a points field first if that data becomes meaningful to anchor.
 *
 * @param actorId   the actor earning the badge
 * @param badgeCode the badge type identifier (e.g., "100_DELIVERIES", "TOP_RATED")
 * @param badgeLabel human-readable badge label
 * @author MANFOUO Braun
 */
public record BadgeAnchorPayload(
        UUID tenantId,
        UUID actorId,
        String badgeCode,
        String badgeLabel) {
}
