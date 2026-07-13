package com.yowyob.tiibntick.core.gofp.domain.model.enums;

/**
 * Plans d'abonnement Market des opérateurs de points relais.
 *
 * Contrairement aux livreurs (quota de livraisons), la valeur pour un point relais
 * est la capacité d'accueil simultanée de colis et la commission TiiBnTick
 * sur les frais de gardiennage.
 *
 * FREE     →  5 colis simultanés max, commission 30%
 * STANDARD → 30 colis simultanés max, commission 20%
 * PREMIUM  → illimité (-1),           commission 10%
 */
public enum RelayHubSubscriptionType {

    FREE    ("FREE",     5,  30.0),
    STANDARD("STANDARD", 30, 20.0),
    PREMIUM ("PREMIUM",  -1, 10.0);   // -1 = illimité

    private final String value;
    private final int    maxPacketsSimultaneous;  // -1 = illimité
    private final double commissionPercent;

    RelayHubSubscriptionType(String value, int maxPacketsSimultaneous, double commissionPercent) {
        this.value                   = value;
        this.maxPacketsSimultaneous  = maxPacketsSimultaneous;
        this.commissionPercent       = commissionPercent;
    }

    public String getValue()                    { return value; }
    public int    getMaxPacketsSimultaneous()   { return maxPacketsSimultaneous; }
    public double getCommissionPercent()        { return commissionPercent; }

    /** Vrai uniquement pour le plan PREMIUM (capacité illimitée). */
    public boolean isUnlimited() {
        return this.maxPacketsSimultaneous == -1;
    }

    /**
     * Vrai si le point relais peut encore accepter un nouveau colis
     * selon le nombre de colis actuellement en stock.
     *
     * @param currentPackets colis actuellement stockés
     */
    public boolean hasRemainingCapacity(int currentPackets) {
        return isUnlimited() || currentPackets < this.maxPacketsSimultaneous;
    }

    /** Calcule la commission TiiBnTick sur des frais de gardiennage bruts. */
    public double calculateCommission(double grossAmount) {
        return grossAmount * (commissionPercent / 100.0);
    }

    /** Calcule le montant net reversé à l'opérateur après commission. */
    public double calculateNetAmount(double grossAmount) {
        return grossAmount - calculateCommission(grossAmount);
    }

    public static RelayHubSubscriptionType fromValue(String value) {
        for (RelayHubSubscriptionType t : values()) {
            if (t.value.equalsIgnoreCase(value)) return t;
        }
        throw new IllegalArgumentException("Unknown RelayHubSubscriptionType: " + value);
    }
}
