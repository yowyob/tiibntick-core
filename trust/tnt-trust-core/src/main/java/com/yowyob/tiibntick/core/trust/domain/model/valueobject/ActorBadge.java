package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object — {@code ActorBadge}.
 *
 * <p>Represents a reputation badge earned by a TiiBnTick deliverer actor and
 * anchored on the Hyperledger Fabric ledger. Badges are portable verifiable
 * credentials: once on-chain, they exist independently of the TiiBnTick platform.
 *
 * <h3>Standard Badge Types</h3>
 * <ul>
 *   <li>{@code 100_DELIVERIES} — 100 successful deliveries</li>
 *   <li>{@code 500_DELIVERIES} — 500 successful deliveries</li>
 *   <li>{@code TOP_RATED} — Average rating ≥ 4.8 over 50+ deliveries</li>
 *   <li>{@code CERTIFIED_RELAY_OPERATOR} — Certified hub relay operator</li>
 *   <li>{@code ZERO_CLAIM} — No disputes in 6 months</li>
 *   <li>{@code ZONE_VETERAN} — 1 year active in a DAO zone</li>
 * </ul>
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class ActorBadge {

    private final String badgeId;
    private final String actorId;
    private final String tenantId;

    /**
     * Badge type identifier.
     * Standard values: {@code 100_DELIVERIES}, {@code TOP_RATED},
     * {@code CERTIFIED_RELAY_OPERATOR}, {@code ZERO_CLAIM}, {@code ZONE_VETERAN}.
     */
    private final String badgeType;

    /**
     * Reputation points associated with this badge.
     * Contributes to the actor's global reputation score.
     */
    private final int points;

    private final LocalDateTime awardedAt;

    /**
     * Fabric transaction hash — populated asynchronously after
     * the {@code BADGE_AWARDED} event is committed to the ledger.
     */
    private String blockchainTxHash;

    /** Whether this badge has been revoked. */
    private boolean revoked;
    private LocalDateTime revokedAt;

    private ActorBadge(
            final String badgeId,
            final String actorId,
            final String tenantId,
            final String badgeType,
            final int points,
            final LocalDateTime awardedAt,
            final String blockchainTxHash,
            final boolean revoked,
            final LocalDateTime revokedAt) {
        this.badgeId = Objects.requireNonNull(badgeId, "badgeId must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.badgeType = Objects.requireNonNull(badgeType, "badgeType must not be null");
        if (points < 0) throw new IllegalArgumentException("points must be non-negative");
        this.points = points;
        this.awardedAt = Objects.requireNonNull(awardedAt, "awardedAt must not be null");
        this.blockchainTxHash = blockchainTxHash;
        this.revoked = revoked;
        this.revokedAt = revokedAt;
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Creates a new {@link ActorBadge} as awarded (not yet confirmed on-chain).
     *
     * @param actorId   the actor receiving the badge
     * @param tenantId  the tenant identifier
     * @param badgeType the badge type identifier
     * @param points    reputation points for this badge
     * @return a new {@link ActorBadge} pending on-chain confirmation
     */
    public static ActorBadge award(
            final String actorId,
            final String tenantId,
            final String badgeType,
            final int points) {
        return new ActorBadge(
                UUID.randomUUID().toString(),
                actorId, tenantId, badgeType, points,
                LocalDateTime.now(), null, false, null);
    }

    /**
     * Reconstitutes an {@link ActorBadge} from persisted state.
     */
    public static ActorBadge reconstitute(
            final String badgeId,
            final String actorId,
            final String tenantId,
            final String badgeType,
            final int points,
            final LocalDateTime awardedAt,
            final String blockchainTxHash,
            final boolean revoked,
            final LocalDateTime revokedAt) {
        return new ActorBadge(badgeId, actorId, tenantId, badgeType, points,
                awardedAt, blockchainTxHash, revoked, revokedAt);
    }

    // ── Domain Behavior ───────────────────────────────────────────────────────

    /**
     * Records the Fabric transaction hash after on-chain confirmation.
     *
     * @param txHash the Fabric tx hash confirming this badge on the ledger
     */
    public void confirmOnChain(final String txHash) {
        Objects.requireNonNull(txHash, "txHash must not be null");
        this.blockchainTxHash = txHash;
    }

    /**
     * Revokes this badge. Once revoked, the badge is permanently inactive.
     *
     * @throws IllegalStateException if already revoked
     */
    public void revoke() {
        if (this.revoked) {
            throw new IllegalStateException("Badge already revoked: " + badgeId);
        }
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
    }

    /**
     * Returns {@code true} if this badge is active and confirmed on-chain.
     */
    public boolean isVerifiable() {
        return !revoked && blockchainTxHash != null && !blockchainTxHash.isBlank();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getBadgeId() { return badgeId; }
    public String getActorId() { return actorId; }
    public String getTenantId() { return tenantId; }
    public String getBadgeType() { return badgeType; }
    public int getPoints() { return points; }
    public LocalDateTime getAwardedAt() { return awardedAt; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public boolean isRevoked() { return revoked; }
    public LocalDateTime getRevokedAt() { return revokedAt; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorBadge other)) return false;
        return Objects.equals(badgeId, other.badgeId);
    }

    @Override
    public int hashCode() { return Objects.hash(badgeId); }

    @Override
    public String toString() {
        return "ActorBadge{badgeId='" + badgeId + "', actorId='" + actorId
                + "', badgeType='" + badgeType + "', verifiable=" + isVerifiable() + "}";
    }
}
