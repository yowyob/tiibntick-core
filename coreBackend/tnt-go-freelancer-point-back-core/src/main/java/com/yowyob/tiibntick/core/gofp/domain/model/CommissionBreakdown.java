package com.yowyob.tiibntick.core.gofp.domain.model;

import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionType;
import lombok.Builder;
import lombok.Value;

/**
 * Détail du calcul de commission sur une livraison Market.
 * Immuable — calculé par CommissionCoreService.
 */
@Value
@Builder
public class CommissionBreakdown {

    /** Montant brut payé par le client. */
    double grossAmount;

    /** Commission retenue par TiiBnTick. */
    double commissionAmount;

    /** Montant net reçu par le livreur. */
    double netAmount;

    /** Taux de commission appliqué (%). */
    double commissionPercent;

    /** Plan d'abonnement qui détermine le taux. */
    SubscriptionType subscriptionType;

    /** Devise (FCFA, NGN…). */
    String currency;

    public static CommissionBreakdown compute(double grossAmount,
                                              SubscriptionType plan,
                                              String currency) {
        double commission = plan.calculateCommission(grossAmount);
        double net        = plan.calculateNetAmount(grossAmount);
        return CommissionBreakdown.builder()
                .grossAmount(grossAmount)
                .commissionAmount(commission)
                .netAmount(net)
                .commissionPercent(plan.getCommissionPercent())
                .subscriptionType(plan)
                .currency(currency)
                .build();
    }
}
