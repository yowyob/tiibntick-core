package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.subscription;

/**
 * Represents the type of subscription a delivery person can have.
 * Each plan defines a monthly delivery quota and a commission rate
 * that TiiBnTick takes on each completed delivery.
 *
 * Plan summary:
 *   FREE     →  5 deliveries/month, 30% commission
 *   STANDARD → 30 deliveries/month, 20% commission
 *   ADVANCE  → unlimited deliveries, 10% commission
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
public enum SubscriptionType {

    //                value        maxDeliveries  commissionPercent
    FREE    ("FREE",     5,            30.0),
    STANDARD("STANDARD", 30,           20.0),
    ADVANCE ("ADVANCE",  -1,           10.0);   // -1 = unlimited

    private final String value;

    /**
     * Maximum number of deliveries allowed per billing period.
     * -1 means unlimited (ADVANCE plan).
     */
    private final int maxDeliveries;

    /**
     * Percentage of each delivery price taken by TiiBnTick as commission.
     */
    private final double commissionPercent;

    SubscriptionType(String value, int maxDeliveries, double commissionPercent) {
        this.value = value;
        this.maxDeliveries = maxDeliveries;
        this.commissionPercent = commissionPercent;
    }

    public String getValue() {
        return value;
    }

    public int getMaxDeliveries() {
        return maxDeliveries;
    }

    public double getCommissionPercent() {
        return commissionPercent;
    }

    /** Returns true only for the ADVANCE plan which has no delivery cap. */
    public boolean isUnlimited() {
        return this.maxDeliveries == -1;
    }

    /**
     * Returns true if the delivery person can still accept a new delivery
     * given the number of deliveries already used this period.
     *
     * @param deliveriesUsed number of deliveries completed in the current period
     */
    public boolean hasRemainingQuota(int deliveriesUsed) {
        return isUnlimited() || deliveriesUsed < this.maxDeliveries;
    }

    /**
     * Calculates the TiiBnTick commission amount on a gross delivery price.
     *
     * @param grossAmount the total price paid by the client
     * @return the commission amount retained by TiiBnTick
     */
    public double calculateCommission(double grossAmount) {
        return grossAmount * (commissionPercent / 100.0);
    }

    /**
     * Calculates the net amount the delivery person actually receives
     * after TiiBnTick's commission is deducted.
     *
     * @param grossAmount the total price paid by the client
     * @return the amount transferred to the delivery person
     */
    public double calculateNetAmount(double grossAmount) {
        return grossAmount - calculateCommission(grossAmount);
    }

    public static SubscriptionType fromValue(String value) {
        for (SubscriptionType type : SubscriptionType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown subscription type: " + value);
    }
}
