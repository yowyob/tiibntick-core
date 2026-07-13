package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Value Object — physical/commercial characteristics of a parcel to be delivered.
 * @author MANFOUO Braun
 */
public record ParcelSpec(
        String description,
        double weightKg,
        double lengthCm,
        double widthCm,
        double heightCm,
        double valueXaf,
        boolean fragile,
        boolean perishable,
        boolean requiresInsurance,
        int quantity
) {
    public double volumeCm3() {
        return lengthCm * widthCm * heightCm;
    }

    /** Standard volumetric weight using 5000 cm³/kg divisor. */
    public double volumetricWeightKg() {
        return volumeCm3() / 5000.0;
    }

    /** Returns the billable weight (greater of actual and volumetric). */
    public double billableWeightKg() {
        return Math.max(weightKg, volumetricWeightKg());
    }
}
