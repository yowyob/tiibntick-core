package com.yowyob.tiibntick.core.gofp.domain.policy;

import com.yowyob.tiibntick.core.gofp.domain.model.CommissionBreakdown;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionType;

/**
 * Politique de commission Market TiiBnTick.
 *
 * Règles métier :
 *   FREE     →  5 livraisons/mois,  commission 30%
 *   STANDARD → 30 livraisons/mois,  commission 20%
 *   ADVANCE  → illimité,            commission 10%
 *
 * Un livreur sans abonnement est traité comme FREE (pire taux).
 * Un livreur SUSPENDED (quota épuisé) ne peut pas accepter de nouvelles livraisons.
 */
public final class CommissionPolicy {

    private CommissionPolicy() {}

    public static final String DEFAULT_CURRENCY = "FCFA";

    /**
     * Calcule le breakdown de commission pour une livraison complétée.
     *
     * @param grossAmount montant brut payé par le client
     * @param plan        plan d'abonnement actif du livreur (null → FREE)
     * @param currency    devise
     */
    public static CommissionBreakdown compute(double grossAmount,
                                              SubscriptionType plan,
                                              String currency) {
        SubscriptionType effectivePlan = (plan != null) ? plan : SubscriptionType.FREE;
        return CommissionBreakdown.compute(grossAmount, effectivePlan,
                currency != null ? currency : DEFAULT_CURRENCY);
    }

    /**
     * Vérifie si le livreur peut encore accepter une livraison ce mois-ci.
     *
     * @param plan           plan d'abonnement
     * @param deliveriesUsed nombre de livraisons effectuées ce mois
     */
    public static boolean canAcceptDelivery(SubscriptionType plan, int deliveriesUsed) {
        if (plan == null) {
            return SubscriptionType.FREE.hasRemainingQuota(deliveriesUsed);
        }
        return plan.hasRemainingQuota(deliveriesUsed);
    }

    /**
     * Retourne le plan par défaut si null.
     */
    public static SubscriptionType effectivePlan(SubscriptionType plan) {
        return plan != null ? plan : SubscriptionType.FREE;
    }
}
