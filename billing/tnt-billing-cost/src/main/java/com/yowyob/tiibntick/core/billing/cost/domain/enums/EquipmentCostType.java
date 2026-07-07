package com.yowyob.tiibntick.core.billing.cost.domain.enums;

/**
 * Equipment types that can incur additional operational costs in the billing engine.
 *
 * <p>Used by {@code ICostUseCase.computeEquipmentCost()} to calculate the extra
 * operational cost for each piece of special FreelancerOrg equipment deployed
 * during a delivery mission.
 *
 * <p>These cost additions feed the {@code otherCosts} component of {@code OperationalCost}.
 *
 * @author MANFOUO Braun
 */
public enum EquipmentCostType {

    /** Portable refrigerated box — electricity + depreciation per km. */
    REFRIGERATED_BOX(150.0, 5.0),

    /** Large cargo bag — minimal cost. */
    CARGO_BAG(20.0, 0.5),

    /** Waterproof cover — negligible cost. */
    WATERPROOF_COVER(10.0, 0.2),

    /** GPS tracking beacon — cellular data cost per mission. */
    TRACKING_BEACON(100.0, 0.0),

    /** Parcel scanner — depreciation per mission. */
    PARCEL_SCANNER(30.0, 0.0),

    /** Thermal insulation bag — minimal cost. */
    THERMAL_BAG(50.0, 1.0),

    /** Fragile foam padding — material cost per use. */
    FRAGILE_FOAM(80.0, 0.0),

    /** Oversized rack — wear per km. */
    OVERSIZED_RACK(40.0, 2.0),

    /** Padlock — negligible cost. */
    PADLOCK(5.0, 0.0);

    /**
     * Base fixed cost per mission for this equipment (XAF).
     * Represents electricity, wear, data, or material cost.
     */
    private final double fixedCostPerMissionXaf;

    /**
     * Variable cost per km for this equipment (XAF/km).
     * Represents fuel or wear cost proportional to distance.
     */
    private final double variableCostPerKmXaf;

    EquipmentCostType(double fixedCostPerMissionXaf, double variableCostPerKmXaf) {
        this.fixedCostPerMissionXaf = fixedCostPerMissionXaf;
        this.variableCostPerKmXaf = variableCostPerKmXaf;
    }

    public double fixedCostPerMissionXaf() { return fixedCostPerMissionXaf; }
    public double variableCostPerKmXaf() { return variableCostPerKmXaf; }

    /**
     * Computes the total equipment cost for a given distance.
     *
     * @param distanceKm delivery distance in km
     * @return total equipment cost in XAF
     */
    public double totalCostXaf(double distanceKm) {
        return fixedCostPerMissionXaf + (variableCostPerKmXaf * distanceKm);
    }
}
