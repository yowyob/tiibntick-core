package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;

/**
 * Physical characteristics of a parcel affecting vehicle capacity constraints in VRP.
 * Weight (kg) and volume (m³) are used to model demands in the CVRP solver.
 *
 * @author MANFOUO Braun
 */
public record PackageSpecification(
        double weightKg,
        double widthCm,
        double heightCm,
        double lengthCm,
        boolean fragile,
        boolean perishable,
        String description
) {

    public PackageSpecification {
        if (weightKg <= 0) {
            throw new DeliveryDomainException("Package weight must be positive, got: " + weightKg);
        }
        if (widthCm <= 0 || heightCm <= 0 || lengthCm <= 0) {
            throw new DeliveryDomainException("Package dimensions must be positive");
        }
    }

    /**
     * Computes the volumetric weight in dm³ (litres).
     * Used as demand value in the VRP capacity constraint.
     */
    public double volumetricWeightDm3() {
        return (widthCm * heightCm * lengthCm) / 1000.0;
    }

    /**
     * Returns the effective demand value for VRP solver (max of real weight and volumetric weight).
     */
    public long vrpDemandUnits() {
        return Math.round(Math.max(weightKg, volumetricWeightDm3()));
    }
}
