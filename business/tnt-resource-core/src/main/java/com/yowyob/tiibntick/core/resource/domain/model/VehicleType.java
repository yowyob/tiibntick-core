package com.yowyob.tiibntick.core.resource.domain.model;

/**
 * Enumeration of vehicle types available for delivery operations in TiiBnTick.
 *
 * <p>Values {@link #MOTO}, {@link #VELO}, {@link #VOITURE}, {@link #CAMIONNETTE} and
 * {@link #VELO_CARGO} were added in  to support the FreelancerOrganization fleet
 * ({@code FreelancerVehicle}) whose type vocabulary is adapted to the informal Cameroonian
 * logistics context (benskinneurs). Existing Agency enum values are preserved for backward
 * compatibility.
 *
 * @author MANFOUO Braun
 */
public enum VehicleType {

    // ── Legacy Agency types (maintained for backward compatibility) ────────

    /** Motorised two-wheeler (Agency equivalent). Preferred: use {@link #MOTO}. */
    MOTORCYCLE,

    /** Non-motorised bicycle (Agency equivalent). Preferred: use {@link #VELO}. */
    BICYCLE,

    /** Passenger car (Agency equivalent). Preferred: use {@link #VOITURE}. */
    CAR,

    /** Three-wheeled vehicle. */
    TRICYCLE,

    /** Light van / minivan (Agency equivalent). Preferred: use {@link #CAMIONNETTE}. */
    VAN,

    /** Heavy truck. */
    TRUCK,

    // ── FreelancerOrganization types (added ) ─────────────────────────

    /**
     * Motorbike / benskin (moto). Primary vehicle in the Cameroonian informal logistics context.
     * DSL variable: {@code vehicleType == MOTO}.
     */
    MOTO,

    /**
     * Standard bicycle (vélo). Eco-friendly last-mile delivery.
     * DSL variable: {@code vehicleType == VELO}.
     */
    VELO,

    /**
     * Car / personal vehicle (voiture).
     * DSL variable: {@code vehicleType == VOITURE}.
     */
    VOITURE,

    /**
     * Light van / pickup (camionnette). Higher-capacity freelancer vehicle.
     * DSL variable: {@code vehicleType == CAMIONNETTE}.
     */
    CAMIONNETTE,

    /**
     * Cargo bicycle with large carrying capacity (vélo cargo).
     * Suitable for bulk last-mile deliveries in dense urban areas.
     * DSL variable: {@code vehicleType == VELO_CARGO}.
     */
    VELO_CARGO
}
