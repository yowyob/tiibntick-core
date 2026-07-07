package com.yowyob.tiibntick.core.resource.domain.model;

/**
 * Fuel type for a FreelancerOrg vehicle.
 *
 * <p>Used by {@code FreelancerVehicle.fuelType} to characterize the energy source
 * of the vehicle. This information feeds the {@code FleetCostParameters} calculation
 * in {@code tnt-billing-cost}, which uses fuel consumption rate + fuel price per liter
 * to compute the operational cost of a delivery mission.
 *
 * @author MANFOUO Braun
 */
public enum FuelType {

    /** Gasoline / petrol (essence). Most common for motos in Cameroon. */
    ESSENCE,

    /** Diesel. Common for vans, camionnettes, and heavy vehicles. */
    DIESEL,

    /** Electric vehicle (véhicule électrique). */
    ELECTRIQUE,

    /** Hybrid vehicle (partially electric). */
    HYBRIDE
}
