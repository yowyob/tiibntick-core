package com.yowyob.tiibntick.core.tp.domain.model;

import com.yowyob.tiibntick.core.tp.domain.exception.InsufficientLoyaltyPointsException;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTransactionType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: LoyaltyAccount.
 *
 * <p>Tracks the loyalty points for a TiiBnTick third party.
 * Points are earned per delivery, redeemed for discounts, and may expire.
 * The tier is automatically recalculated from the cumulative balance.</p>
 *
 * @author MANFOUO Braun
 */
public final class LoyaltyAccount {

    /** Points earned per successful standard delivery. */
    public static final int POINTS_PER_DELIVERY = 10;

    /** Points earned per express delivery. */
    public static final int POINTS_PER_EXPRESS_DELIVERY = 20;

    /** XAF equivalent per loyalty point when redeeming. */
    public static final int XAF_PER_POINT = 5;

    private final UUID id;
    private final UUID tenantId;
    private final UUID thirdPartyId;

    /** Current available points balance. */
    private final int availablePoints;

    /** Cumulative points ever earned (for tier calculation). */
    private final int lifetimePoints;

    /** Points already redeemed. */
    private final int redeemedPoints;

    /** Points expired. */
    private final int expiredPoints;

    private final LoyaltyTier currentTier;
    private final List<LoyaltyTransaction> transactions;
    private final Instant createdAt;
    private final Instant updatedAt;

    private LoyaltyAccount(
            UUID id,
            UUID tenantId,
            UUID thirdPartyId,
            int availablePoints,
            int lifetimePoints,
            int redeemedPoints,
            int expiredPoints,
            LoyaltyTier currentTier,
            List<LoyaltyTransaction> transactions,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.thirdPartyId = Objects.requireNonNull(thirdPartyId, "thirdPartyId is required");
        this.availablePoints = Math.max(0, availablePoints);
        this.lifetimePoints = Math.max(0, lifetimePoints);
        this.redeemedPoints = Math.max(0, redeemedPoints);
        this.expiredPoints = Math.max(0, expiredPoints);
        this.currentTier = currentTier != null ? currentTier : LoyaltyTier.BRONZE;
        this.transactions = transactions != null
                ? Collections.unmodifiableList(new ArrayList<>(transactions))
                : List.of();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    // ─── Factory ─────────────────────────────────────────────────────────────

    /**
     * Creates a new empty LoyaltyAccount for a third party.
     */
    public static LoyaltyAccount create(UUID tenantId, UUID thirdPartyId) {
        Instant now = Instant.now();
        return new LoyaltyAccount(
                UUID.randomUUID(), tenantId, thirdPartyId,
                0, 0, 0, 0,
                LoyaltyTier.BRONZE, new ArrayList<>(), now, now);
    }

    /**
     * Reconstitutes from persistence.
     */
    public static LoyaltyAccount reconstitute(
            UUID id, UUID tenantId, UUID thirdPartyId,
            int availablePoints, int lifetimePoints,
            int redeemedPoints, int expiredPoints,
            LoyaltyTier tier, List<LoyaltyTransaction> transactions,
            Instant createdAt, Instant updatedAt) {
        return new LoyaltyAccount(id, tenantId, thirdPartyId,
                availablePoints, lifetimePoints, redeemedPoints, expiredPoints,
                tier, transactions, createdAt, updatedAt);
    }

    // ─── Business methods ─────────────────────────────────────────────────────

    /**
     * Credits loyalty points (e.g., after a delivery).
     *
     * @param points the number of points to credit (must be > 0)
     * @param type   the transaction type
     * @param ref    external reference (e.g., missionId)
     * @return updated LoyaltyAccount
     */
    public LoyaltyAccount credit(int points, LoyaltyTransactionType type, String ref) {
        if (points <= 0) {
            throw new IllegalArgumentException("Points to credit must be positive, got: " + points);
        }
        int newAvailable = availablePoints + points;
        int newLifetime = lifetimePoints + points;
        LoyaltyTier newTier = LoyaltyTier.fromPoints(newLifetime);

        LoyaltyTransaction tx = new LoyaltyTransaction(
                UUID.randomUUID(), id, points, type, ref, Instant.now());

        List<LoyaltyTransaction> updatedTxs = new ArrayList<>(transactions);
        updatedTxs.add(tx);

        return new LoyaltyAccount(id, tenantId, thirdPartyId,
                newAvailable, newLifetime, redeemedPoints, expiredPoints,
                newTier, updatedTxs, createdAt, Instant.now());
    }

    /**
     * Redeems loyalty points for a discount.
     *
     * @param points the number of points to redeem
     * @param ref    external reference (e.g., invoiceId)
     * @return updated LoyaltyAccount
     * @throws InsufficientLoyaltyPointsException if points exceed available balance
     */
    public LoyaltyAccount redeem(int points, String ref) {
        if (points <= 0) {
            throw new IllegalArgumentException("Points to redeem must be positive, got: " + points);
        }
        if (points > availablePoints) {
            throw new InsufficientLoyaltyPointsException(thirdPartyId, points, availablePoints);
        }
        int newAvailable = availablePoints - points;
        int newRedeemed = redeemedPoints + points;

        LoyaltyTransaction tx = new LoyaltyTransaction(
                UUID.randomUUID(), id, -points,
                LoyaltyTransactionType.REDEEMED_FOR_DISCOUNT, ref, Instant.now());

        List<LoyaltyTransaction> updatedTxs = new ArrayList<>(transactions);
        updatedTxs.add(tx);

        return new LoyaltyAccount(id, tenantId, thirdPartyId,
                newAvailable, lifetimePoints, newRedeemed, expiredPoints,
                currentTier, updatedTxs, createdAt, Instant.now());
    }

    /**
     * Converts a number of points to their XAF monetary equivalent.
     *
     * @param points the points to convert
     * @return XAF value
     */
    public int pointsToXaf(int points) {
        return points * XAF_PER_POINT;
    }

    /**
     * Calculates the maximum redeemable discount in XAF.
     */
    public int maxDiscountXaf() {
        return pointsToXaf(availablePoints);
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getThirdPartyId() { return thirdPartyId; }
    public int getAvailablePoints() { return availablePoints; }
    public int getLifetimePoints() { return lifetimePoints; }
    public int getRedeemedPoints() { return redeemedPoints; }
    public int getExpiredPoints() { return expiredPoints; }
    public LoyaltyTier getCurrentTier() { return currentTier; }
    public List<LoyaltyTransaction> getTransactions() { return transactions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoyaltyAccount that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
