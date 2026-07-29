package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint;

/**
 * Subscription plans available for a RelayPoint.
 *
 * Plan summary:
 *   BASIC    →  50 deposits/month,  15% commission
 *   STANDARD → 200 deposits/month,  10% commission
 *   PREMIUM  → unlimited deposits,   5% commission
 */
public enum RelayPointSubscriptionType {

    BASIC   ("BASIC",    50,  15.0),
    STANDARD("STANDARD", 200, 10.0),
    PREMIUM ("PREMIUM",  -1,   5.0);  // -1 = unlimited

    private final String value;
    private final int maxDeposits;
    private final double commissionPercent;

    RelayPointSubscriptionType(String value, int maxDeposits, double commissionPercent) {
        this.value = value;
        this.maxDeposits = maxDeposits;
        this.commissionPercent = commissionPercent;
    }

    public String getValue()             { return value; }
    public int getMaxDeposits()          { return maxDeposits; }
    public double getCommissionPercent() { return commissionPercent; }
    public boolean isUnlimited()         { return maxDeposits == -1; }

    public boolean hasRemainingQuota(int depositsUsed) {
        return isUnlimited() || depositsUsed < maxDeposits;
    }

    public static RelayPointSubscriptionType fromValue(String value) {
        for (RelayPointSubscriptionType t : values()) {
            if (t.value.equalsIgnoreCase(value)) return t;
        }
        throw new IllegalArgumentException("Unknown relay point subscription type: " + value);
    }
}
