package com.yowyob.tiibntick.core.gofp.domain.policy;

import com.yowyob.tiibntick.core.gofp.domain.model.enums.RelayHubSubscriptionType;

/**
 * Politique d'abonnement Market des points relais.
 *
 * Règles métier :
 *   FREE     →  5 colis simultanés max, commission 30%
 *   STANDARD → 30 colis simultanés max, commission 20%
 *   PREMIUM  → illimité,                commission 10%
 *
 * Un point relais sans abonnement est traité comme FREE (pire taux, capacité minimale).
 */
public final class RelayHubSubscriptionPolicy {

    private RelayHubSubscriptionPolicy() {}

    public static final String DEFAULT_CURRENCY = "FCFA";

    /**
     * Vérifie si le point relais peut accepter un nouveau colis
     * selon son plan et le nombre de colis actuellement stockés.
     *
     * @param plan           plan d'abonnement actif (null → FREE)
     * @param packetsUsed    nombre de colis actuellement en stock
     */
    public static boolean canAcceptPacket(RelayHubSubscriptionType plan, int packetsUsed) {
        RelayHubSubscriptionType effective = effectivePlan(plan);
        return effective.hasRemainingCapacity(packetsUsed);
    }

    /**
     * Calcule la commission TiiBnTick sur des frais de gardiennage bruts.
     *
     * @param grossAmount frais de gardiennage bruts
     * @param plan        plan actif du point relais (null → FREE)
     * @return montant de la commission retenue par TiiBnTick
     */
    public static double computeCommission(double grossAmount, RelayHubSubscriptionType plan) {
        return effectivePlan(plan).calculateCommission(grossAmount);
    }

    /**
     * Calcule le montant net reversé à l'opérateur après commission TiiBnTick.
     *
     * @param grossAmount frais de gardiennage bruts
     * @param plan        plan actif du point relais (null → FREE)
     * @return montant net pour l'opérateur
     */
    public static double computeNetAmount(double grossAmount, RelayHubSubscriptionType plan) {
        return effectivePlan(plan).calculateNetAmount(grossAmount);
    }

    /**
     * Retourne le plan effectif — FREE si null.
     */
    public static RelayHubSubscriptionType effectivePlan(RelayHubSubscriptionType plan) {
        return plan != null ? plan : RelayHubSubscriptionType.FREE;
    }

    /**
     * Retourne le prix mensuel fixe selon le plan (en FCFA).
     * Référence tarifaire : peut être externalisé dans une table de configuration.
     */
    public static double monthlyPrice(RelayHubSubscriptionType plan) {
        return switch (plan) {
            case FREE     -> 0.0;
            case STANDARD -> 5_000.0;
            case PREMIUM  -> 15_000.0;
        };
    }
}
