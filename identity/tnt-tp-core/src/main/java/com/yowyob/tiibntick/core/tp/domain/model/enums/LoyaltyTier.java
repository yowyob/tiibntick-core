package com.yowyob.tiibntick.core.tp.domain.model.enums;

/**
 * Loyalty tier levels for TiiBnTick clients.
 * Tiers determine discount eligibility and premium service access.
 *
 * @author MANFOUO Braun
 */
public enum LoyaltyTier {

    /** 0–499 points. Standard benefits. */
    BRONZE(0, 499),

    /** 500–1999 points. 5% discount on deliveries. */
    SILVER(500, 1999),

    /** 2000–4999 points. 10% discount + priority handling. */
    GOLD(2000, 4999),

    /** 5000+ points. 15% discount + premium support + free express. */
    PLATINUM(5000, Integer.MAX_VALUE);

    private final int minPoints;
    private final int maxPoints;

    LoyaltyTier(int minPoints, int maxPoints) {
        this.minPoints = minPoints;
        this.maxPoints = maxPoints;
    }

    public int getMinPoints() {
        return minPoints;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    /**
     * Resolves the tier for a given points balance.
     *
     * @param points the current loyalty points balance
     * @return the corresponding tier
     */
    public static LoyaltyTier fromPoints(int points) {
        for (LoyaltyTier tier : values()) {
            if (points >= tier.minPoints && points <= tier.maxPoints) {
                return tier;
            }
        }
        return BRONZE;
    }
}
