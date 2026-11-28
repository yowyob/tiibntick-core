package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;

/**
 * Physical characteristics of a parcel affecting vehicle capacity constraints in VRP.
 * Weight (kg) and volume (m³) are used to model demands in the CVRP solver.
 *
 * <p>Units: weight in kilograms, every dimension (width, height, length, thickness) in centimetres.
 * {@code designation} (short commercial label of the goods, e.g. "Carton électroménager") and
 * {@code thicknessCm} are optional.
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
        String description,
        String designation,
        Double thicknessCm
) {

    /** Backward-compatible constructor: no designation, no thickness. */
    public PackageSpecification(double weightKg, double widthCm, double heightCm, double lengthCm,
                                boolean fragile, boolean perishable, String description) {
        this(weightKg, widthCm, heightCm, lengthCm, fragile, perishable, description, null, null);
    }

    public PackageSpecification {
        if (weightKg <= 0) {
            throw new DeliveryDomainException("Package weight must be positive, got: " + weightKg);
        }
        if (widthCm <= 0 || heightCm <= 0 || lengthCm <= 0) {
            throw new DeliveryDomainException("Package dimensions must be positive");
        }
        if (thicknessCm != null && thicknessCm <= 0) {
            throw new DeliveryDomainException("Package thickness must be positive when given, got: " + thicknessCm);
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
