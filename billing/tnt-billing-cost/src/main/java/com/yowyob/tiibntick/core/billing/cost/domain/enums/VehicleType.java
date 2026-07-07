package com.yowyob.tiibntick.core.billing.cost.domain.enums;

/**
 * Vehicle type determining fuel consumption and base wear rates.
 * Parameters calibrated for Cameroonian urban context.
 *
 * <p> — Added freelancer-specific vehicle types (MOTO, VELO, VOITURE, CAMIONNETTE,
 * VELO_CARGO) aligned with tnt-resource-core's FreelancerVehicle fleet types.
 * Legacy types preserved for backward compatibility with Agency fleet.
 *
 * @author MANFOUO Braun
 */
public enum VehicleType {

    // ── Legacy Agency types (backward compatible) ─────────────────────────

    /** Moto-taxi / motorcycle — dominant in Yaoundé/Douala. */
    MOTORCYCLE(2.5, 0.008),
    /** Tricycle / cargo-tricycle. */
    TRICYCLE(4.0, 0.012),
    /** Light car (sedan, SUV). */
    CAR(7.5, 0.015),
    /** Light van / minibus. */
    VAN(10.0, 0.020),
    /** Small truck / pickup. */
    TRUCK(12.0, 0.025),
    /** Bicycle / cargo bike — zero fuel. */
    BICYCLE(0.0, 0.002),

    // ── FreelancerOrg types () ────────────────────────────────────────

    /**
     * Moto (motorbike/benskin) — primary vehicle in the FreelancerOrg fleet.
     * Equivalent to MOTORCYCLE but using the Cameroonian/French naming.
     * DSL variable: {@code vehicleType == MOTO}.
     */
    MOTO(2.5, 0.008),

    /**
     * Vélo (bicycle, non-motorized). Zero fuel consumption.
     * DSL variable: {@code vehicleType == VELO}.
     */
    VELO(0.0, 0.002),

    /**
     * Voiture (car/sedan). Higher capacity for larger packages.
     * DSL variable: {@code vehicleType == VOITURE}.
     */
    VOITURE(7.0, 0.014),

    /**
     * Camionnette (light van/pickup). Highest freelancer capacity.
     * DSL variable: {@code vehicleType == CAMIONNETTE}.
     */
    CAMIONNETTE(9.5, 0.018),

    /**
     * Vélo cargo (cargo bicycle with load frame). Zero fuel, higher capacity than VELO.
     * DSL variable: {@code vehicleType == VELO_CARGO}.
     */
    VELO_CARGO(0.0, 0.003);

    /** Base fuel consumption in L/100km. */
    private final double baseConsumptionL100km;
    /** Base mechanical wear cost per km (as fraction, multiply by fuel price). */
    private final double baseWearPerKm;

    VehicleType(double baseConsumptionL100km, double baseWearPerKm) {
        this.baseConsumptionL100km = baseConsumptionL100km;
        this.baseWearPerKm = baseWearPerKm;
    }

    public double baseConsumptionL100km() { return baseConsumptionL100km; }
    public double baseWearPerKm() { return baseWearPerKm; }

    /**
     * Returns {@code true} if this vehicle type runs on fuel
     * (and therefore has non-zero fuel cost).
     */
    public boolean usesFuel() {
        return baseConsumptionL100km > 0;
    }

    /**
     * Returns the default cargo capacity for this vehicle type in kg.
     * Used when {@code vehicleCapacityKg} is not explicitly provided.
     */
    public double defaultCapacityKg() {
        return switch (this) {
            case BICYCLE, VELO -> 30.0;
            case VELO_CARGO    -> 80.0;
            case MOTORCYCLE, MOTO -> 50.0;
            case TRICYCLE      -> 150.0;
            case CAR, VOITURE  -> 200.0;
            case VAN, CAMIONNETTE -> 500.0;
            case TRUCK         -> 1000.0;
        };
    }
}
