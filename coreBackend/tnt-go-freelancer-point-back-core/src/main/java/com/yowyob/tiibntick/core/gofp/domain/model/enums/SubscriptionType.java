package com.yowyob.tiibntick.core.gofp.domain.model.enums;

/**
 * Plans d'abonnement Market des livreurs freelancers.
 *
 * FREE     →  5 livraisons/mois,     commission 30%
 * STANDARD → 30 livraisons/mois,     commission 20%
 * ADVANCE  → illimité,               commission 10%
 */
public enum SubscriptionType {

    FREE    ("FREE",     5,  30.0),
    STANDARD("STANDARD", 30, 20.0),
    ADVANCE ("ADVANCE",  -1, 10.0);   // -1 = illimité

    private final String value;
    private final int    maxDeliveries;      // -1 = illimité
    private final double commissionPercent;

    SubscriptionType(String value, int maxDeliveries, double commissionPercent) {
        this.value              = value;
        this.maxDeliveries      = maxDeliveries;
        this.commissionPercent  = commissionPercent;
    }

    public String getValue()             { return value; }
    public int    getMaxDeliveries()     { return maxDeliveries; }
    public double getCommissionPercent() { return commissionPercent; }

    public boolean isUnlimited() {
        return this.maxDeliveries == -1;
    }

    public boolean hasRemainingQuota(int deliveriesUsed) {
        return isUnlimited() || deliveriesUsed < this.maxDeliveries;
    }

    /** Calcule la commission TiiBnTick sur un montant brut. */
    public double calculateCommission(double grossAmount) {
        return grossAmount * (commissionPercent / 100.0);
    }

    /** Calcule le montant net reçu par le livreur après commission. */
    public double calculateNetAmount(double grossAmount) {
        return grossAmount - calculateCommission(grossAmount);
    }

    public static SubscriptionType fromValue(String value) {
        for (SubscriptionType t : values()) {
            if (t.value.equalsIgnoreCase(value)) return t;
        }
        throw new IllegalArgumentException("Unknown SubscriptionType: " + value);
    }
}
