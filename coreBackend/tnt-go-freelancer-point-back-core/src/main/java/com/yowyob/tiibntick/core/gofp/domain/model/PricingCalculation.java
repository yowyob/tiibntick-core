package com.yowyob.tiibntick.core.gofp.domain.model;

import lombok.Builder;
import lombok.Value;

/**
 * Résultat d'un calcul de prix pour une livraison Market.
 * Produit par PricingCoreService.
 */
@Value
@Builder
public class PricingCalculation {

    double baseFee;
    double kmCharge;
    double kgCharge;
    double cbmCharge;
    double fragileSurcharge;
    double perishableSurcharge;

    /** Total HT. */
    double totalAmount;

    String currency;

    double distanceKm;
    double weightKg;
    double volumeCbm;

    public static PricingCalculation compute(
            double baseFee,
            double pricePerKm,   double distanceKm,
            double pricePerKg,   double weightKg,
            double pricePerCbm,  double volumeCbm,
            double fragileSurcharge,
            double perishableSurcharge,
            String currency) {

        double kmCharge      = pricePerKm  * distanceKm;
        double kgCharge      = pricePerKg  * weightKg;
        double cbmCharge     = pricePerCbm * volumeCbm;
        double total = baseFee + kmCharge + kgCharge + cbmCharge
                     + fragileSurcharge + perishableSurcharge;

        return PricingCalculation.builder()
                .baseFee(baseFee)
                .kmCharge(kmCharge)
                .kgCharge(kgCharge)
                .cbmCharge(cbmCharge)
                .fragileSurcharge(fragileSurcharge)
                .perishableSurcharge(perishableSurcharge)
                .totalAmount(total)
                .currency(currency)
                .distanceKm(distanceKm)
                .weightKg(weightKg)
                .volumeCbm(volumeCbm)
                .build();
    }
}
